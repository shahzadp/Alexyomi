package eu.kanade.tachiyomi.ui.mal

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.anilist.AnilistMediaStatus
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListAnimeItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListMangaItemStatus
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.core.common.util.system.logcat
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Keeps the watched progress of the user's MyAnimeList and the local episodes/chapters of the
 * app in sync, so nothing has to be marked twice.
 *
 * Both directions use the tracker table of the app: a local anime or manga that was added to
 * MyAnimeList gets a MAL track, which maps the MAL id to the local entry.
 *
 * - app -> MAL: watching an episode updates the MAL progress (the usual tracker mechanism)
 * - MAL -> app: the progress of the list is applied to the local episodes, so the episodes
 *   that were watched elsewhere are ticked automatically
 */
object MalProgressSync {

    /** Id of the MyAnimeList tracker. */
    const val MAL_TRACKER_ID = 1L

    private val getAnimeTracks: GetAnimeTracks = Injekt.get()
    private val insertAnimeTrack: InsertAnimeTrack = Injekt.get()
    private val animeTrackRepository: AnimeTrackRepository = Injekt.get()
    private val getMangaTracks: GetMangaTracks = Injekt.get()
    private val insertMangaTrack: InsertMangaTrack = Injekt.get()
    private val mangaTrackRepository: MangaTrackRepository = Injekt.get()
    private val getEpisodes: GetEpisodesByAnimeId = Injekt.get()
    private val updateEpisode: UpdateEpisode = Injekt.get()
    private val getChapters: GetChaptersByMangaId = Injekt.get()
    private val updateChapter: UpdateChapter = Injekt.get()
    private val animeRepository: AnimeRepository = Injekt.get()
    private val mangaRepository: MangaRepository = Injekt.get()
    private val updateAnimeFromRemote: UpdateAnimeFromRemote = Injekt.get()

    /**
     * How many anime are fetched per run, so the sources are not hammered.
     */
    private const val MAX_FETCHES_PER_RUN = 15
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList

    /**
     * Links a local anime to its MyAnimeList entry.
     *
     * Called when an anime is added to (or updated on) the list from an entry page, so the
     * progress of the entry and the local episodes stay in sync from then on.
     */
    suspend fun linkAnime(
        animeId: Long,
        animeTitle: String,
        malId: Int,
        episodesWatched: Int,
        skipStatusLookup: Boolean = false,
        statusId: Long? = null,
        totalEpisodes: Long = 0L,
        markAll: Boolean = false,
    ) {
        try {
            val status = if (skipStatusLookup) null else tracker.findListStatus(malId)
            val existing = getAnimeTracks.await(animeId).firstOrNull { it.trackerId == MAL_TRACKER_ID }

            insertAnimeTrack.await(
                AnimeTrack(
                    id = existing?.id ?: 0L,
                    animeId = animeId,
                    trackerId = MAL_TRACKER_ID,
                    remoteId = malId.toLong(),
                    libraryId = existing?.libraryId,
                    title = animeTitle,
                    lastEpisodeSeen = episodesWatched.toDouble(),
                    totalEpisodes = totalEpisodes.takeIf { it > 0 } ?: existing?.totalEpisodes ?: 0L,
                    status = statusId ?: status.toStatusId(),
                    score = (status?.score ?: 0).toDouble(),
                    remoteUrl = "https://myanimelist.net/anime/$malId",
                    startDate = existing?.startDate ?: 0L,
                    finishDate = existing?.finishDate ?: 0L,
                    private = existing?.private ?: false,
                ),
            )
            logEvent("Linked anime $animeId with MyAnimeList entry $malId")

            // The episodes that were already watched on the list are ticked right away. An entry
            // that is completed means that every episode is watched, even when the count of the
            // list is behind.
            val completed = markAll || status?.status.equals("completed", ignoreCase = true) == true
            applyAnimeProgress(animeId, episodesWatched, completed)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to link anime $animeId with MyAnimeList" }
        }
    }

    /**
     * Links a local manga to its MyAnimeList entry.
     */
    suspend fun linkManga(
        mangaId: Long,
        mangaTitle: String,
        malId: Int,
        chaptersRead: Int,
        skipStatusLookup: Boolean = false,
        statusId: Long? = null,
        markAll: Boolean = false,
    ) {
        try {
            val status = if (skipStatusLookup) null else tracker.findMangaListStatus(malId)
            val existing = getMangaTracks.await(mangaId).firstOrNull { it.trackerId == MAL_TRACKER_ID }

            insertMangaTrack.await(
                MangaTrack(
                    id = existing?.id ?: 0L,
                    mangaId = mangaId,
                    trackerId = MAL_TRACKER_ID,
                    remoteId = malId.toLong(),
                    libraryId = existing?.libraryId,
                    title = mangaTitle,
                    lastChapterRead = chaptersRead.toDouble(),
                    totalChapters = existing?.totalChapters ?: 0L,
                    status = statusId ?: status.toStatusId(),
                    score = (status?.score ?: 0).toDouble(),
                    remoteUrl = "https://myanimelist.net/manga/$malId",
                    startDate = existing?.startDate ?: 0L,
                    finishDate = existing?.finishDate ?: 0L,
                    private = existing?.private ?: false,
                ),
            )
            logEvent("Linked manga $mangaId with MyAnimeList entry $malId")

            // The chapters that were already read on the list are ticked right away. An entry
            // that is completed means that every chapter is read, even when the count of the
            // list is behind.
            val completed = markAll || status?.status.equals("completed", ignoreCase = true) == true
            applyMangaProgress(mangaId, chaptersRead, completed)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to link manga $mangaId with MyAnimeList" }
        }
    }

    /**
     * Applies the progress of the user's list to the local episodes of the linked animes.
     *
     * Entries that are not linked yet are linked with the local anime of the same title, so
     * the progress also arrives for anime the user has not opened since the list was synced.
     */
    /**
     * Applies the progress of the user's list to the local episodes of the linked animes.
     *
     * Anime that are not linked yet are linked with the entry of the same title, so the
     * progress also arrives for the extensions the user has not opened since the last sync.
     * The same anime can be in several extensions - each of them gets its own link.
     *
     * A link that exists is never changed here. The entry is looked up properly when the
     * anime page is opened, where AniList knows the mapping between both.
     */
    suspend fun applyListProgress(entries: List<MalListScreenModel.MalListEntry>) {
        if (entries.isEmpty()) return
        try {
            val tracks = animeTrackRepository.getAnimeTracksAsFlow().first()
                .filter { it.trackerId == MAL_TRACKER_ID }
            val tracksByRemoteId = tracks.groupBy { it.remoteId }
            val linkedAnimeIds = tracks.map { it.animeId }.toSet()

            // Apply the progress of the entries that are linked already. The same anime can be
            // in several extensions, so an entry can have more than one link - every one of
            // them gets the progress.
            for (entry in entries) {
                val linked = tracksByRemoteId[entry.id] ?: continue
                // A completed entry means every episode is watched, even when the count of the
                // list is behind.
                val markAll = entry.status.equals("completed", ignoreCase = true)
                for (track in linked) {
                    if (markAll || entry.progress > track.lastEpisodeSeen) {
                        applyAnimeProgress(track.animeId, entry.progress, markAll)
                    }
                }
            }

            // Link the local anime that have no link yet. The same anime can be in several
            // extensions - each of them gets its own link, so the filter is on the anime and
            // not on the entry.
            val local = localAnimeTitles()
            if (local.isEmpty()) return
            val unlinkedAnime = local.filter { it.first !in linkedAnimeIds }
            if (unlinkedAnime.isEmpty()) return

            // The best entry for every local anime. The titles of the extensions are usually
            // the romaji ones, so the AniList titles are used as well.
            val anilistTitles = anilistTitlesOf(entries.map { it.id.toInt() })
            val bestEntryFor = bestEntryForAnime(entries, unlinkedAnime, anilistTitles)

            for ((animeId, match) in bestEntryFor) {
                if (animeId in linkedAnimeIds) continue
                val entry = match.item
                val animeTitle = local.first { it.first == animeId }.second
                logEvent(
                    "Linked anime $animeId ('$animeTitle') with MyAnimeList entry " +
                        "${entry.id} ('${entry.title}', score ${match.score})",
                )
                linkAnime(
                    animeId = animeId,
                    animeTitle = animeTitle,
                    malId = entry.id.toInt(),
                    episodesWatched = entry.progress,
                    skipStatusLookup = true,
                    statusId = entry.status.toStatusId(),
                    totalEpisodes = entry.total?.toLong() ?: 0L,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to apply the MyAnimeList progress" }
        }
    }

    /**
     * The entry of the list that fits the local anime the best.
     *
     * The local anime are indexed by their words, so only the ones that have a word in common
     * with an entry are compared. Comparing every entry with every anime takes seconds when
     * the list is big - and that freezes the app while the list is scrolled.
     */
    private fun bestEntryForAnime(
        entries: List<MalListScreenModel.MalListEntry>,
        local: List<Pair<Long, String>>,
        anilistTitles: Map<Int, AnilistMediaStatus>,
    ): Map<Long, MalTitleMatcher.Match<MalListScreenModel.MalListEntry>> {
        if (entries.isEmpty() || local.isEmpty()) return emptyMap()

        // The index of the local anime, by their words
        val localTitles = local.map { MalTitleMatcher.normalized(it.second) }
        val index = HashMap<String, MutableList<Int>>()
        for (i in localTitles.indices) {
            for (token in MalTitleMatcher.tokensNormalized(localTitles[i])) {
                index.getOrPut(token) { mutableListOf() }.add(i)
            }
        }

        val best = HashMap<Long, MalTitleMatcher.Match<MalListScreenModel.MalListEntry>>()
        for (entry in entries) {
            val romaji = anilistTitles[entry.id.toInt()]?.titleRomaji.orEmpty()
            val entryTitle = MalTitleMatcher.normalized(entry.title)
            val romajiTitle = MalTitleMatcher.normalized(romaji)

            // The local anime that have a word in common with the entry
            val candidates = LinkedHashSet<Int>()
            for (token in MalTitleMatcher.tokensNormalized(entryTitle)) {
                index[token]?.let(candidates::addAll)
            }
            if (romajiTitle.isNotEmpty()) {
                for (token in MalTitleMatcher.tokensNormalized(romajiTitle)) {
                    index[token]?.let(candidates::addAll)
                }
            }

            for (i in candidates) {
                val animeId = local[i].first
                val score = maxOf(
                    MalTitleMatcher.scoreNormalized(entryTitle, localTitles[i]),
                    MalTitleMatcher.scoreNormalized(romajiTitle, localTitles[i]),
                )
                if (score < MalTitleMatcher.MIN_SCORE) continue
                val current = best[animeId]
                if (current == null || score > current.score) {
                    best[animeId] = MalTitleMatcher.Match(entry, entry.title, score)
                }
            }
        }
        return best
    }

    /**
     * The local anime and their titles.
     */
    private suspend fun localAnimeTitles(): List<Pair<Long, String>> {
        return try {
            animeRepository.getAllAnimes().map { it.id to it.title }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load the local anime" }
            emptyList()
        }
    }

    /**
     * Fetches the episode list of the anime that are on the list but have no episodes yet.
     *
     * Without the episodes there is nothing to tick, so the anime is fetched from its source
     * and the progress is applied afterwards.
     */
    suspend fun fetchMissingEpisodes() {
        val tracks = animeTrackRepository.getAnimeTracksAsFlow().first()
            .filter { it.trackerId == MAL_TRACKER_ID }
        var fetched = 0

        for (track in tracks) {
            if (fetched >= MAX_FETCHES_PER_RUN) break
            if (getEpisodes.await(track.animeId).isNotEmpty()) continue

            val progress = progressOfAnime(track.remoteId) ?: continue
            if (progress <= 0) continue

            val anime = try {
                animeRepository.getAnimeById(track.animeId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load anime ${track.animeId}" }
                null
            } ?: continue

            logEvent("Fetching the episodes of anime ${anime.id} ('${anime.title}')")
            updateAnimeFromRemote.awaitEpisodesUpdate(anime, fetchEpisodes = true)
            fetched++

            applyAnimeProgress(anime.id, progress, isCompleted(track.remoteId))
        }
    }

    /**
     * Whether the entry of the list is on the list as completed.
     */
    private suspend fun isCompleted(malId: Long): Boolean {
        val cached = MalListStore.read(MalListScreenModel.MediaType.ANIME)
        cached?.firstOrNull { it.id == malId }?.let { entry ->
            return entry.status.equals("completed", ignoreCase = true)
        }
        val mangaCache = MalListStore.read(MalListScreenModel.MediaType.MANGA)
        mangaCache?.firstOrNull { it.id == malId }?.let { entry ->
            return entry.status.equals("completed", ignoreCase = true)
        }
        return false
    }

    /**
     * The AniList data of the entries, which carries the romaji titles.
     */
    private suspend fun anilistTitlesOf(malIds: List<Int>): Map<Int, AnilistMediaStatus> {
        if (malIds.isEmpty()) return emptyMap()
        return try {
            AnilistBrowseApi(Injekt.get<NetworkHelper>().client).listStatusByMalId(malIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load the AniList data of the list" }
            emptyMap()
        }
    }

    /**
     * Applies the progress of the user's manga list to the local chapters of the linked
     * mangas, and links the mangas that are not linked yet.
     */
    suspend fun applyMangaListProgress(entries: List<MalListScreenModel.MalListEntry>) {
        if (entries.isEmpty()) return
        try {
            val tracks = mangaTrackRepository.getMangaTracksAsFlow().first()
                .filter { it.trackerId == MAL_TRACKER_ID }
            val linkedMangaIds = tracks.map { it.mangaId }.toSet()

            val tracksByRemoteId = tracks.groupBy { it.remoteId }
            for (entry in entries) {
                val linked = tracksByRemoteId[entry.id] ?: continue
                // A completed entry means every chapter is read, even when the count of the
                // list is behind.
                val markAll = entry.status.equals("completed", ignoreCase = true)
                for (track in linked) {
                    if (markAll || entry.progress > track.lastChapterRead) {
                        applyMangaProgress(track.mangaId, entry.progress, markAll)
                    }
                }
            }

            // Link the local manga that have no link yet. The filter is on the manga and not
            // on the entry - the same manga can be in several extensions.
            val local = localMangaTitles()
            if (local.isEmpty()) return
            val unlinkedManga = local.filter { it.first !in linkedMangaIds }
            if (unlinkedManga.isEmpty()) return

            // The best entry for every local manga. The extensions usually show the romaji
            // title, so the AniList titles are used as well.
            val anilistTitles = anilistTitlesOf(entries.map { it.id.toInt() })
            val bestEntryFor = bestEntryForAnime(entries, unlinkedManga, anilistTitles)

            for ((mangaId, match) in bestEntryFor) {
                if (mangaId in linkedMangaIds) continue
                val entry = match.item
                val mangaTitle = local.first { it.first == mangaId }.second
                logEvent(
                    "Linked manga $mangaId ('$mangaTitle') with MyAnimeList entry " +
                        "${entry.id} ('${entry.title}', score ${match.score})",
                )
                linkManga(
                    mangaId = mangaId,
                    mangaTitle = mangaTitle,
                    malId = entry.id.toInt(),
                    chaptersRead = entry.progress,
                    skipStatusLookup = true,
                    statusId = entry.status.toStatusId(),
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to apply the MyAnimeList manga progress" }
        }
    }

    /**
     * The local manga and their titles.
     */
    private suspend fun localMangaTitles(): List<Pair<Long, String>> {
        return try {
            mangaRepository.getAllMangas().map { it.id to it.title }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load the local manga" }
            emptyList()
        }
    }

    /**
     * Applies the progress of the MyAnimeList entry of the anime to the local episodes.
     *
     * The cached list is used first, MyAnimeList is only asked when the list isn't cached.
     *
     * @return the progress that was applied, or null when it is unknown
     */
    suspend fun applyProgressForAnime(animeId: Long, malId: Long, markAll: Boolean = false): Int? {
        val progress = progressOfAnime(malId) ?: return null
        applyAnimeProgress(animeId, progress, markAll)
        return progress
    }

    /**
     * Applies the progress of the MyAnimeList entry of the manga to the local chapters.
     */
    suspend fun applyProgressForManga(mangaId: Long, malId: Long, markAll: Boolean = false): Int? {
        val progress = progressOfManga(malId) ?: return null
        applyMangaProgress(mangaId, progress, markAll)
        return progress
    }

    private suspend fun progressOfAnime(malId: Long): Int? {
        val cached = MalListStore.read(MalListScreenModel.MediaType.ANIME)
        cached?.firstOrNull { it.id == malId }?.let { return it.progress }
        return runCatching { tracker.findListStatus(malId.toInt())?.numEpisodesWatched?.toInt() }
            .getOrNull()
    }

    private suspend fun progressOfManga(malId: Long): Int? {
        val cached = MalListStore.read(MalListScreenModel.MediaType.MANGA)
        cached?.firstOrNull { it.id == malId }?.let { return it.progress }
        return runCatching { tracker.findMangaListStatus(malId.toInt())?.numChaptersRead?.toInt() }
            .getOrNull()
    }

    /**
     * The last things the sync did, so the debug info can show what is going on.
     */
    private val recentEvents = ArrayDeque<String>()

    /**
     * The last sync actions, newest first.
     */
    fun recentEvents(): List<String> = recentEvents.toList()

    fun logEvent(message: String) {
        synchronized(recentEvents) {
            recentEvents.addFirst(message)
            while (recentEvents.size > 20) recentEvents.removeLast()
        }
        logcat(LogPriority.INFO) { message }
    }

    /**
     * Marks every episode up to [progress] of the anime as watched.
     */
    /**
     * Marks the watched episodes of the anime.
     *
     * @param progress how many episodes are watched
     * @param markAll whether every episode is watched - the entry is on the list as completed,
     * so the count of the list can be behind
     */
    suspend fun applyAnimeProgress(animeId: Long, progress: Int, markAll: Boolean = false) {
        if (progress <= 0 && !markAll) return
        val episodes = getEpisodes.await(animeId)
        if (episodes.isEmpty()) {
            logEvent("Anime $animeId has no episodes yet, $progress could not be ticked")
            return
        }

        val ordered = episodes.sortedBy { it.episodeNumber }

        val toUpdate = if (markAll) {
            // The entry is completed, so every episode of the anime is watched
            ordered.filter { !it.seen }
        } else {
            // The episodes that are within the progress
            val inRange = ordered.filter { !it.seen && it.episodeNumber >= 1.0 && it.episodeNumber <= progress }
            if (inRange.size >= progress) {
                inRange
            } else {
                // Extensions that number their episodes differently (offset or absolute numbering)
                // don't line up with the count of the list, so the first episodes are taken.
                ordered.take(progress.coerceAtMost(ordered.size)).filter { !it.seen }
            }
        }

        if (toUpdate.isEmpty()) return

        updateEpisode.awaitAll(toUpdate.map { EpisodeUpdate(id = it.id, seen = true) })
        logEvent("Marked ${toUpdate.size} episode(s) of anime $animeId as watched (progress $progress)")
    }

    /**
     * Marks every chapter up to [progress] of the manga as read.
     */
    /**
     * Marks the read chapters of the manga.
     *
     * @param progress how many chapters are read
     * @param markAll whether every chapter is read - the entry is on the list as completed,
     * so the count of the list can be behind
     */
    suspend fun applyMangaProgress(mangaId: Long, progress: Int, markAll: Boolean = false) {
        if (progress <= 0 && !markAll) return
        val chapters = getChapters.await(mangaId)
        if (chapters.isEmpty()) {
            logEvent("Manga $mangaId has no chapters yet, $progress could not be ticked")
            return
        }

        val ordered = chapters.sortedBy { it.chapterNumber }

        val toUpdate = if (markAll) {
            // The entry is completed, so every chapter of the manga is read
            ordered.filter { !it.read }
        } else {
            // The chapters that are within the progress
            val inRange = ordered.filter { !it.read && it.chapterNumber >= 1.0 && it.chapterNumber <= progress }
            if (inRange.size >= progress) {
                inRange
            } else {
                // Extensions that number their chapters differently don't line up with the count
                // of the list, so the first chapters are taken.
                ordered.take(progress.coerceAtMost(ordered.size)).filter { !it.read }
            }
        }

        if (toUpdate.isEmpty()) return

        updateChapter.awaitAll(toUpdate.map { ChapterUpdate(id = it.id, read = true) })
        logEvent("Marked ${toUpdate.size} chapter(s) of manga $mangaId as read (progress $progress)")
    }

    /**
     * Converts the MyAnimeList status into the id the tracker uses.
     */
    private fun String?.toStatusId(): Long =
        when (this) {
            "watching" -> MyAnimeList.WATCHING
            "reading" -> MyAnimeList.READING
            "completed" -> MyAnimeList.COMPLETED
            "on_hold" -> MyAnimeList.ON_HOLD
            "dropped" -> MyAnimeList.DROPPED
            "plan_to_watch" -> MyAnimeList.PLAN_TO_WATCH
            "plan_to_read" -> MyAnimeList.PLAN_TO_READ
            "rewatching" -> MyAnimeList.REWATCHING
            "rereading" -> MyAnimeList.REREADING
            else -> MyAnimeList.PLAN_TO_WATCH
        }

    /**
     * Converts the MyAnimeList status into the id the tracker uses.
     */
    private fun MALListAnimeItemStatus?.toStatusId(): Long = this?.status.toStatusId()

    /**
     * Converts the MyAnimeList status into the id the tracker uses.
     */
    private fun MALListMangaItemStatus?.toStatusId(): Long = this?.status.toStatusId()
}
