package eu.kanade.tachiyomi.ui.mal

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListAnimeItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListMangaItemStatus
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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
    ) {
        try {
            val status = tracker.findListStatus(malId)
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
                    totalEpisodes = existing?.totalEpisodes ?: 0L,
                    status = status.toStatusId(),
                    score = (status?.score ?: 0).toDouble(),
                    remoteUrl = "https://myanimelist.net/anime/$malId",
                    startDate = existing?.startDate ?: 0L,
                    finishDate = existing?.finishDate ?: 0L,
                    private = existing?.private ?: false,
                ),
            )
            logEvent("Linked anime $animeId with MyAnimeList entry $malId")

            // The episodes that were already watched on the list are ticked right away
            applyAnimeProgress(animeId, episodesWatched)
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
    ) {
        try {
            val status = tracker.findMangaListStatus(malId)
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
                    status = status.toStatusId(),
                    score = (status?.score ?: 0).toDouble(),
                    remoteUrl = "https://myanimelist.net/manga/$malId",
                    startDate = existing?.startDate ?: 0L,
                    finishDate = existing?.finishDate ?: 0L,
                    private = existing?.private ?: false,
                ),
            )
            logEvent("Linked manga $mangaId with MyAnimeList entry $malId")

            applyMangaProgress(mangaId, chaptersRead)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to link manga $mangaId with MyAnimeList" }
        }
    }

    /**
     * Applies the progress of the user's list to the local episodes of the linked animes.
     *
     * Only entries that are ahead of the local progress are applied, so episodes that were
     * watched in the app are never unticked.
     */
    suspend fun applyListProgress(entries: List<MalListScreenModel.MalListEntry>) {
        if (entries.isEmpty()) return
        try {
            val tracks = animeTrackRepository.getAnimeTracksAsFlow().first()
                .filter { it.trackerId == MAL_TRACKER_ID }
            if (tracks.isEmpty()) return

            val tracksByRemoteId = tracks.associateBy { it.remoteId }
            for (entry in entries) {
                val track = tracksByRemoteId[entry.id] ?: continue
                if (entry.progress <= track.lastEpisodeSeen) continue
                applyAnimeProgress(track.animeId, entry.progress)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to apply the MyAnimeList progress" }
        }
    }

    /**
     * Applies the progress of the user's manga list to the local chapters of the linked
     * mangas.
     */
    suspend fun applyMangaListProgress(entries: List<MalListScreenModel.MalListEntry>) {
        if (entries.isEmpty()) return
        try {
            val tracks = mangaTrackRepository.getMangaTracksAsFlow().first()
                .filter { it.trackerId == MAL_TRACKER_ID }
            if (tracks.isEmpty()) return

            val tracksByRemoteId = tracks.associateBy { it.remoteId }
            for (entry in entries) {
                val track = tracksByRemoteId[entry.id] ?: continue
                if (entry.progress <= track.lastChapterRead) continue
                applyMangaProgress(track.mangaId, entry.progress)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to apply the MyAnimeList manga progress" }
        }
    }

    /**
     * Applies the progress of the MyAnimeList entry of the anime to the local episodes.
     *
     * The cached list is used first, MyAnimeList is only asked when the list isn't cached.
     */
    suspend fun applyProgressForAnime(animeId: Long, malId: Long) {
        val progress = progressOfAnime(malId) ?: return
        applyAnimeProgress(animeId, progress)
    }

    /**
     * Applies the progress of the MyAnimeList entry of the manga to the local chapters.
     */
    suspend fun applyProgressForManga(mangaId: Long, malId: Long) {
        val progress = progressOfManga(malId) ?: return
        applyMangaProgress(mangaId, progress)
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

    private fun logEvent(message: String) {
        synchronized(recentEvents) {
            recentEvents.addFirst(message)
            while (recentEvents.size > 20) recentEvents.removeLast()
        }
        logcat(LogPriority.INFO) { message }
    }

    /**
     * Marks every episode up to [progress] of the anime as watched.
     */
    suspend fun applyAnimeProgress(animeId: Long, progress: Int) {
        if (progress <= 0) return
        val episodes = getEpisodes.await(animeId)
        if (episodes.isEmpty()) return

        val toUpdate = episodes.filter { episode ->
            !episode.seen && episode.episodeNumber >= 1.0 && episode.episodeNumber <= progress
        }
        if (toUpdate.isEmpty()) return

        updateEpisode.awaitAll(toUpdate.map { EpisodeUpdate(id = it.id, seen = true) })
        logEvent("Marked ${toUpdate.size} episode(s) of anime $animeId as watched (progress $progress)")
    }

    /**
     * Marks every chapter up to [progress] of the manga as read.
     */
    suspend fun applyMangaProgress(mangaId: Long, progress: Int) {
        if (progress <= 0) return
        val chapters = getChapters.await(mangaId)
        if (chapters.isEmpty()) return

        val toUpdate = chapters.filter { chapter ->
            !chapter.read && chapter.chapterNumber >= 1.0 && chapter.chapterNumber <= progress
        }
        if (toUpdate.isEmpty()) return

        updateChapter.awaitAll(toUpdate.map { ChapterUpdate(id = it.id, read = true) })
        logEvent("Marked ${toUpdate.size} chapter(s) of manga $mangaId as read (progress $progress)")
    }

    /**
     * Converts the MyAnimeList status into the id the tracker uses.
     */
    private fun MALListAnimeItemStatus?.toStatusId(): Long =
        when (this?.status) {
            "watching" -> MyAnimeList.WATCHING
            "completed" -> MyAnimeList.COMPLETED
            "on_hold" -> MyAnimeList.ON_HOLD
            "dropped" -> MyAnimeList.DROPPED
            "plan_to_watch" -> MyAnimeList.PLAN_TO_WATCH
            "rewatching" -> MyAnimeList.REWATCHING
            else -> MyAnimeList.PLAN_TO_WATCH
        }

    /**
     * Converts the MyAnimeList status into the id the tracker uses.
     */
    private fun MALListMangaItemStatus?.toStatusId(): Long =
        when (this?.status) {
            "reading" -> MyAnimeList.READING
            "completed" -> MyAnimeList.COMPLETED
            "on_hold" -> MyAnimeList.ON_HOLD
            "dropped" -> MyAnimeList.DROPPED
            "plan_to_read" -> MyAnimeList.PLAN_TO_READ
            "rereading" -> MyAnimeList.REREADING
            else -> MyAnimeList.PLAN_TO_READ
        }
}
