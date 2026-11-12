package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALAnimeListItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaListItem
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MalListScreenModel(
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList,
    private val anilistApi: eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi =
        eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
) : StateScreenModel<MalListScreenModel.State>(State()) {

    enum class MediaType { ANIME, MANGA }

    enum class Sort {
        STATUS,
        ALPHABETICAL,
        SCORE,
        PROGRESS,
        LAST_UPDATED,
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var lastRevision = MalRevision.revision.value

    init {
        screenModelScope.launchIO {
            tracker.isLoggedInFlow.collect { signedIn ->
                mutableState.update {
                    it.copy(
                        signedIn = signedIn,
                        loginError = if (signedIn) null else MyAnimeList.lastLoginError,
                    )
                }
                if (signedIn && mutableState.value.entries.isEmpty()) {
                    restoreFromCacheOrLoad()
                }
                if (!signedIn) {
                    mutableState.update { it.copy(loading = false, entries = persistentListOf(), error = false) }
                }
            }
        }
        screenModelScope.launchIO {
            // Re-fetch whenever any screen changes the user's MAL list,
            // so this tab reflects edits made elsewhere immediately.
            MalRevision.revision.collectLatest { revision ->
                if (revision != lastRevision) {
                    lastRevision = revision
                    if (tracker.isLoggedIn) {
                        // A cheap Wi-Fi-only diff — only applies when something actually changed.
                        maybeRefreshInBackground()
                    }
                }
            }
        }
    }

    private fun restoreFromCacheOrLoad() {
        screenModelScope.launchIO {
            val mediaType = mutableState.value.mediaType
            val cached = MalListStore.read(mediaType)
            if (cached != null) {
                // Show the cached list instantly; a cheap Wi-Fi-only background diff
                // refreshes it only when there really is something new to update.
                mutableState.update {
                    it.copy(
                        entries = cached.toImmutableList(),
                        loading = false,
                        error = false,
                    )
                }
                maybeRefreshInBackground()
            } else {
                load()
            }
        }
    }

    private fun persistCurrentList() {
        val mediaType = mutableState.value.mediaType
        screenModelScope.launchIO {
            MalListStore.write(mediaType, mutableState.value.entries)
        }
    }

    private suspend fun fetchFresh(mediaType: MediaType): List<MalListEntry> {
        // The full list is always fetched; filtering happens client-side.
        // This keeps the disk cache complete for offline viewing.
        val entries = mutableListOf<MalListEntry>()
        var offset = 0
        while (entries.size < MAX_ENTRIES) {
            val entriesThisPage = if (mediaType == MediaType.ANIME) {
                val page = tracker.animeListPage(
                    status = null,
                    offset = offset,
                )
                Pair(page.data.map { it.toEntry() }, page.paging.next.isNullOrBlank())
            } else {
                val page = tracker.mangaListPage(
                    status = null,
                    offset = offset,
                )
                Pair(page.data.map { it.toEntry() }, page.paging.next.isNullOrBlank())
            }
            entries += entriesThisPage.first
            if (entriesThisPage.second || entriesThisPage.first.isEmpty()) break
            offset += PAGE_SIZE
        }
        return entries
    }

    /** Keeps the locally cached fields (totals, artwork) whenever MAL doesn't send them. */
    private fun mergeWithCache(
        fresh: List<MalListEntry>,
        cached: List<MalListEntry>,
    ): List<MalListEntry> {
        if (cached.isEmpty()) return fresh
        val byId = cached.associateBy { it.id }
        return fresh.map { newEntry ->
            val old = byId[newEntry.id] ?: return@map newEntry
            newEntry.copy(
                total = newEntry.total ?: old.total,
                airingStatus = newEntry.airingStatus.takeIf { it.isNotBlank() } ?: old.airingStatus,
                mediaType = newEntry.mediaType?.takeIf { it.isNotBlank() } ?: old.mediaType,
                seasonLabel = newEntry.seasonLabel ?: old.seasonLabel,
                coverUrl = newEntry.coverUrl.takeIf { it.isNotBlank() } ?: old.coverUrl,
                updatedAt = newEntry.updatedAt ?: old.updatedAt,
            )
        }
    }

    private data class EntrySignature(
        val id: Long,
        val status: String,
        val progress: Int,
        val score: Int,
        val updatedAt: String,
    )

    private fun signature(entry: MalListEntry) = EntrySignature(
        entry.id,
        entry.status,
        entry.progress,
        entry.score,
        entry.updatedAt.orEmpty(),
    )

    /** Fills missing totals/airing status from AniList, only for entries that lack them. */
    private suspend fun enrichMissing(working: MutableList<MalListEntry>, mediaType: MediaType) {
        val missing = working.filter { it.total == null || it.airingStatus.isBlank() }
        if (missing.isEmpty()) return
        val info = try {
            if (mediaType == MediaType.ANIME) {
                anilistApi.listStatusByMalId(missing.map { it.id.toInt() })
            } else {
                anilistApi.mangaChaptersByMalId(missing.map { it.id.toInt() })
            }
        } catch (e: Exception) {
            emptyMap()
        }
        if (info.isNotEmpty()) {
            for (i in working.indices) {
                val entry = working[i]
                val enrich = info[entry.id.toInt()] ?: continue
                working[i] = entry.copy(
                    total = entry.total ?: enrich.episodes,
                    airingStatus = entry.airingStatus.takeIf { it.isNotBlank() } ?: enrich.airingStatus,
                    mediaType = entry.mediaType?.takeIf { it.isNotBlank() } ?: enrich.format,
                    seasonLabel = entry.seasonLabel
                        ?: enrich.season?.let { s -> enrich.seasonYear?.let { y -> "$s $y" } ?: s },
                )
            }
        }
    }

    /**
     * Cheap remote diff: re-downloads only the list envelope (ids/status/score/
     * progress/updated_at) and applies changes with no UI churn.
     * Returns true when remote changes were applied.
     */
    private suspend fun refreshIfChanged(): Boolean {
        if (!tracker.isLoggedIn) return false
        val mediaType = mutableState.value.mediaType
        val before = mutableState.value.entries
        return try {
            val fresh = mergeWithCache(fetchFresh(mediaType), before)
            val beforeSignature = before.mapTo(HashSet(), ::signature)
            val afterSignature = fresh.mapTo(HashSet(), ::signature)
            if (beforeSignature == afterSignature) {
                // Nothing new to update — skip the UI and the cache entirely
                applyProgressToLocalEpisodes(fresh)
                return@refreshIfChanged false
            }
            // Something changed upstream — apply it, enriching only entries that lack totals
            val working = fresh.toMutableList()
            enrichMissing(working, mediaType)
            val complete = working.toImmutableList()
            MalListStore.write(mediaType, complete)
            mutableState.update { it.copy(entries = complete, loading = false, error = false) }
            applyProgressToLocalEpisodes(complete)
            true
        } catch (e: Exception) {
            // Background check failed — keep showing the cache
            false
        }
    }

    /** Background refresh, only on Wi-Fi, only when there is something new to update. */
    private fun maybeRefreshInBackground() {
        screenModelScope.launchIO {
            if (Injekt.get<android.app.Application>().isConnectedToWifi()) {
                refreshIfChanged()
            }
        }
    }

    fun load() {
        if (!tracker.isLoggedIn) {
            mutableState.update { it.copy(signedIn = false, loading = false, error = false) }
            return
        }
        val mediaType = mutableState.value.mediaType
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val fresh = fetchFresh(mediaType)
                val cached = mutableState.value.entries.takeIf { it.isNotEmpty() }
                    ?: (MalListStore.read(mediaType) ?: emptyList())
                val merged = mergeWithCache(fresh, cached)
                if (cached.isNotEmpty()) {
                    val beforeSignature = cached.mapTo(HashSet(), ::signature)
                    val afterSignature = merged.mapTo(HashSet(), ::signature)
                    if (beforeSignature == afterSignature) {
                        // No remote changes — skip enrichment and any UI churn
                        mutableState.update { it.copy(loading = false, error = false) }
                        return@launchIO
                    }
                }
                // MAL node fields (episode/chapter count, airing status) are not always
                // present — fill gaps from AniList's free API in batches.
                val working = merged.toMutableList()
                enrichMissing(working, mediaType)
                val complete = working.toImmutableList()
                MalListStore.write(mediaType, complete)
                mutableState.update {
                    it.copy(loading = false, entries = complete, error = false)
                }
                applyProgressToLocalEpisodes(complete)
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /**
     * Ticks the episodes/chapters that were watched elsewhere (on MAL) in the local episode
     * lists, so the user doesn't have to mark them a second time.
     */
    private suspend fun applyProgressToLocalEpisodes(entries: List<MalListEntry>) {
        when (mutableState.value.mediaType) {
            MediaType.ANIME -> MalProgressSync.applyListProgress(entries)
            MediaType.MANGA -> MalProgressSync.applyMangaListProgress(entries)
        }
    }

    fun setFilter(filter: Filter) {
        if (mutableState.value.selectedFilter == filter) return
        // Filtering is client-side — no refetch needed
        mutableState.update { it.copy(selectedFilter = filter) }
    }

    fun setMediaType(mediaType: MediaType) {
        if (mutableState.value.mediaType == mediaType) return
        mutableState.update {
            it.copy(
                mediaType = mediaType,
                entries = persistentListOf(),
                loading = true,
                error = false,
            )
        }
        screenModelScope.launchIO {
            val cached = MalListStore.read(mediaType)
            if (cached != null) {
                mutableState.update {
                    it.copy(entries = cached.toImmutableList(), loading = false, error = false)
                }
                maybeRefreshInBackground()
            } else {
                load()
            }
        }
    }

    fun setSearchOpen(open: Boolean) {
        mutableState.update { it.copy(searchOpen = open, query = if (open) it.query else "") }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun setSortBy(sort: Sort) {
        mutableState.update {
            it.copy(
                sortBy = sort,
                // Sensible natural direction per sort key; user can flip afterwards
                sortAscending = when (sort) {
                    Sort.STATUS, Sort.ALPHABETICAL -> true
                    Sort.SCORE, Sort.PROGRESS, Sort.LAST_UPDATED -> false
                },
            )
        }
    }

    fun toggleSortDirection() {
        mutableState.update { it.copy(sortAscending = !it.sortAscending) }
    }

    fun unit(): String = if (mutableState.value.mediaType == MediaType.ANIME) "ep" else "ch"

    fun incrementEpisode(entry: MalListEntry) {
        updateEntry(entry, progress = entry.progress + 1)
    }

    fun saveEntry(
        entry: MalListEntry,
        status: String,
        score: Int,
        progress: Int,
    ) {
        updateEntry(entry, status = status, score = score, progress = progress)
    }

    private fun updateEntry(
        entry: MalListEntry,
        status: String? = null,
        score: Int? = null,
        progress: Int? = null,
    ) {
        val previous = entry
        // Optimistic update so the UI reflects the change immediately
        mutableState.update { state ->
            state.copy(
                updatingIds = state.updatingIds + entry.id,
                entries = state.entries.map {
                    if (it.id == entry.id) {
                        it.copy(
                            status = status ?: it.status,
                            score = score ?: it.score,
                            progress = progress ?: it.progress,
                            // Editing bumps the recency signal so "last updated" ordering behaves
                            updatedAt = java.time.Instant.now().toString(),
                        )
                    } else {
                        it
                    }
                }.toImmutableList(),
            )
        }
        screenModelScope.launchIO {
            try {
                if (mutableState.value.mediaType == MediaType.ANIME) {
                    tracker.updateListStatus(
                        animeId = entry.id.toInt(),
                        status = status,
                        score = score,
                        numWatchedEpisodes = progress,
                    )
                } else {
                    tracker.updateMangaListStatus(
                        mangaId = entry.id.toInt(),
                        status = status,
                        score = score,
                        numChaptersRead = progress,
                    )
                }
                MalRevision.bump()
                // Ignore our own bump — this screen's state is already updated optimistically
                lastRevision = MalRevision.revision.value
                _events.send(Event.Updated)
            } catch (e: Exception) {
                // Roll back on failure
                mutableState.update { state ->
                    state.copy(
                        entries = state.entries.map {
                            if (it.id == previous.id) previous else it
                        }.toImmutableList(),
                    )
                }
                _events.send(Event.UpdateFailed)
            } finally {
                mutableState.update { it.copy(updatingIds = it.updatingIds - entry.id) }
                persistCurrentList()
            }
        }
    }

    private fun seasonOf(date: String?): String? {
        if (date.isNullOrBlank()) return null
        val year = date.take(4)
        if (year.toIntOrNull() == null) return null
        val month = date.substringAfter('-', missingDelimiterValue = "")
            .substringBefore('-')
            .toIntOrNull()
        return if (month != null) {
            val season = when (month) {
                in 1..3 -> "Winter"
                in 4..6 -> "Spring"
                in 7..9 -> "Summer"
                else -> "Fall"
            }
            "$season $year"
        } else {
            year
        }
    }

    private fun MALMangaListItem.toEntry(): MalListEntry {
        return MalListEntry(
            id = node.id,
            title = node.title,
            coverUrl = node.covers?.medium?.takeIf { it.isNotBlank() } ?: node.covers?.large.orEmpty(),
            progress = listStatus.numChaptersRead.toInt(),
            total = node.numChapters.toInt().takeIf { it > 0 },
            status = listStatus.status,
            airingStatus = node.status,
            isReplaying = listStatus.isRereading,
            score = listStatus.score,
            mediaType = node.mediaType.takeIf { it.isNotBlank() },
            seasonLabel = seasonOf(node.startDate),
            updatedAt = listStatus.updatedAt,
        )
    }

    private fun MALAnimeListItem.toEntry(): MalListEntry {
        return MalListEntry(
            id = node.id,
            title = node.title,
            coverUrl = node.covers?.medium?.takeIf { it.isNotBlank() } ?: node.covers?.large.orEmpty(),
            progress = listStatus.numEpisodesWatched.toInt(),
            total = node.numEpisodes.toInt().takeIf { it > 0 },
            status = listStatus.status,
            airingStatus = node.status,
            isReplaying = listStatus.isRewatching,
            score = listStatus.score,
            mediaType = node.mediaType.takeIf { it.isNotBlank() },
            seasonLabel = seasonOf(node.startDate),
            updatedAt = listStatus.updatedAt,
        )
    }

    fun removeFromList(entry: MalListEntry) {
        screenModelScope.launchIO {
            try {
                if (mutableState.value.mediaType == MediaType.ANIME) {
                    tracker.deleteListEntry(entry.id.toInt())
                } else {
                    tracker.deleteMangaListEntry(entry.id.toInt())
                }
                mutableState.update {
                    it.copy(entries = it.entries.filterNot { e -> e.id == entry.id }.toImmutableList())
                }
                persistCurrentList()
                MalRevision.bump()
                // Ignore our own bump — this screen's state is already updated optimistically
                lastRevision = MalRevision.revision.value
                _events.send(Event.Removed)
            } catch (e: Exception) {
                _events.send(Event.UpdateFailed)
            }
        }
    }

    sealed interface Event {
        data object Updated : Event
        data object UpdateFailed : Event
        data object Removed : Event
    }

    @Serializable
    data class MalListEntry(
        val id: Long,
        val title: String,
        val coverUrl: String,
        val progress: Int,
        val total: Int?,
        val status: String,
        val airingStatus: String,
        val isReplaying: Boolean,
        val score: Int,
        val mediaType: String?,
        val seasonLabel: String?,
        val updatedAt: String? = null,
    )

    enum class Filter {
        ALL,
        WATCHING,
        PLAN_TO_WATCH,
        COMPLETED,
        ON_HOLD,
        DROPPED,
        ;

        fun status(type: MediaType): String? = when (this) {
            ALL -> null
            WATCHING -> if (type == MediaType.ANIME) "watching" else "reading"
            PLAN_TO_WATCH -> if (type == MediaType.ANIME) "plan_to_watch" else "plan_to_read"
            COMPLETED -> "completed"
            ON_HOLD -> "on_hold"
            DROPPED -> "dropped"
        }
    }

    data class State(
        val signedIn: Boolean = false,
        val loginError: String? = null,
        val loading: Boolean = true,
        val error: Boolean = false,
        val selectedFilter: Filter = Filter.ALL,
        val mediaType: MediaType = MediaType.ANIME,
        val query: String = "",
        val searchOpen: Boolean = false,
        val sortBy: Sort = Sort.STATUS,
        val sortAscending: Boolean = true,
        val entries: ImmutableList<MalListEntry> = persistentListOf(),
        val updatingIds: Set<Long> = emptySet(),
    )

    companion object {
        private const val PAGE_SIZE = 250
        // Safety bound only; the fetch loop actually ends when MAL stops returning a next page
        private const val MAX_ENTRIES = 10000
    }
}
