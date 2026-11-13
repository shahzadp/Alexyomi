package eu.kanade.tachiyomi.ui.mal

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListAnimeItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListMangaItemStatus
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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
 * Links a local anime or manga with its MyAnimeList entry.
 *
 * The link is what makes the progress of the app and the progress of the list the same
 * number - watching an episode in the app moves the counter of MyAnimeList.
 *
 * The link is only made when the user picks the entry themselves (the "Add to MAL" button
 * on the entry page). The app never guesses which entry belongs to an anime - the seasons of
 * a show look almost the same, and a wrong link is worse than no link at all.
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
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList

    /**
     * Links a local anime with its MyAnimeList entry.
     *
     * Called when the user adds the anime to (or updates it on) the list from an entry page,
     * so the progress of the app and the progress of the list stay the same from then on.
     */
    suspend fun linkAnime(
        animeId: Long,
        animeTitle: String,
        malId: Int,
        episodesWatched: Int,
        skipStatusLookup: Boolean = false,
        statusId: Long? = null,
        totalEpisodes: Long = 0L,
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
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to link anime $animeId with MyAnimeList" }
        }
    }

    /**
     * Links a local manga with its MyAnimeList entry.
     */
    suspend fun linkManga(
        mangaId: Long,
        mangaTitle: String,
        malId: Int,
        chaptersRead: Int,
        skipStatusLookup: Boolean = false,
        statusId: Long? = null,
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
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to link manga $mangaId with MyAnimeList" }
        }
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
