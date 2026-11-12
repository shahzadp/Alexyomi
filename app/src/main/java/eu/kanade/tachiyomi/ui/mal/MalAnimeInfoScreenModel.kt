package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistAnimeDetails
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListAnimeItemStatus
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MalAnimeInfoScreenModel(
    private val anilistId: Int?,
    private val malId: Int?,
    /**
     * The local anime this entry belongs to, when it was opened from an entry page.
     *
     * When set, the entry is linked with the local anime so the watched episodes of both are
     * kept in sync.
     */
    private val localAnimeId: Long? = null,
    private val localAnimeTitle: String? = null,
    private val anilistApi: AnilistBrowseApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList,
) : StateScreenModel<MalAnimeInfoScreenModel.State>(State(signedIn = tracker.isLoggedIn)) {

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var lastRevision = MalRevision.revision.value

    init {
        load()
        screenModelScope.launchIO {
            // Pick up changes made from other screens (e.g. My List edits, +1).
            MalRevision.revision.collectLatest { revision ->
                if (revision != lastRevision) {
                    lastRevision = revision
                    refreshListStatus(mutableState.value.details?.malId)
                }
            }
        }
    }

    fun load() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val details = loadDetails()
                mutableState.update { it.copy(loading = false, details = details) }
                refreshListStatus(details.malId)
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /**
     * The details of the entry.
     *
     * AniList doesn't know every anime that is on MyAnimeList, so the entry is loaded from
     * MyAnimeList itself when AniList can't help.
     */
    private suspend fun loadDetails(): AnilistAnimeDetails {
        return try {
            anilistApi.animeDetails(anilistId, malId)
        } catch (e: Exception) {
            val id = malId ?: throw e
            val fromMal = tracker.getAnimeDetails(id) ?: throw e
            fromMal.toDetails()
        }
    }

    private suspend fun refreshListStatus(statusMalId: Int?) {
        val id = statusMalId ?: malId ?: return
        if (!tracker.isLoggedIn) {
            mutableState.update { it.copy(signedIn = false, listStatus = null) }
            return
        }
        val status = tracker.findListStatus(id)
        mutableState.update { it.copy(signedIn = true, listStatus = status) }
    }

    /**
     * Writes the entry to the user's MAL list. Status defaults to plan_to_watch
     * when creating a new entry without one picked.
     */
    fun saveListEntry(
        status: String,
        score: Int,
        episodesWatched: Int,
    ) {
        val details = mutableState.value.details
        val id = details?.malId ?: malId
        if (id == null) {
            screenModelScope.launchIO { _events.send(Event.UpdateFailed) }
            return
        }
        screenModelScope.launchIO {
            try {
                tracker.updateListStatus(
                    animeId = id,
                    status = status.ifBlank { "plan_to_watch" },
                    score = score,
                    numWatchedEpisodes = episodesWatched,
                )
                MalRevision.bump()
                refreshListStatus(id)
                linkLocalAnime(id, episodesWatched)
                _events.send(Event.Updated)
            } catch (e: Exception) {
                _events.send(Event.UpdateFailed)
            }
        }
    }

    /**
     * Links the entry with the local anime it was opened from, so the watched progress of the
     * list and the local episodes stay in sync.
     */
    private suspend fun linkLocalAnime(malId: Int, episodesWatched: Int) {
        val animeId = localAnimeId ?: return
        val title = localAnimeTitle
            ?: mutableState.value.details?.displayTitle
            ?: return
        MalProgressSync.linkAnime(
            animeId = animeId,
            animeTitle = title,
            malId = malId,
            episodesWatched = episodesWatched,
        )
    }

    /**
     * Removes the anime from the user's MAL list entirely.
     */
    fun removeFromList() {
        val details = mutableState.value.details
        val id = details?.malId ?: malId
        if (id == null) {
            screenModelScope.launchIO { _events.send(Event.UpdateFailed) }
            return
        }
        screenModelScope.launchIO {
            try {
                tracker.deleteListEntry(id)
                mutableState.update { it.copy(listStatus = null) }
                MalRevision.bump()
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

    data class State(
        val loading: Boolean = true,
        val error: Boolean = false,
        val details: AnilistAnimeDetails? = null,
        val signedIn: Boolean = false,
        val listStatus: MALListAnimeItemStatus? = null,
    )
}

/**
 * Converts the details MyAnimeList returns into the model the entry page uses.
 */
private fun eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch.toDetails(): AnilistAnimeDetails {
    return AnilistAnimeDetails(
        anilistId = 0,
        malId = remote_id.toInt(),
        titleRomaji = title,
        titleEnglish = null,
        titleNative = null,
        coverUrl = cover_url,
        bannerUrl = null,
        description = summary,
        episodes = total_episodes.toInt().takeIf { it > 0 },
        duration = null,
        status = publishing_status.takeIf { it.isNotBlank() },
        format = publishing_type.takeIf { it.isNotBlank() },
        averageScore = score.takeIf { it > 0 }?.let { (it * 10).toInt() },
        popularity = null,
        genres = emptyList(),
        season = null,
        seasonYear = null,
        studios = emptyList(),
        nextAiringEpisode = null,
        airingAt = null,
        siteUrl = "https://myanimelist.net/anime/$remote_id",
    )
}
