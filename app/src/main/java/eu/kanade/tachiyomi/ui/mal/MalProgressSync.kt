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

            applyMangaProgress(mangaId, chaptersRead)
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
                for (track in linked) {
                    if (entry.progress > track.lastEpisodeSeen) {
                        applyAnimeProgress(track.animeId, entry.progress)
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
            val bestEntryFor = mutableMapOf<Long, MalTitleMatcher.Match<MalListScreenModel.MalListEntry>>()
            for (entry in entries) {
                val romaji = anilistTitles[entry.id.toInt()]?.titleRomaji.orEmpty()
                for ((animeId, animeTitle) in unlinkedAnime) {
                    val score = maxOf(
                        MalTitleMatcher.score(entry.title, animeTitle),
                        MalTitleMatcher.score(romaji, animeTitle),
                    )
                    if (score < MalTitleMatcher.MIN_SCORE) continue
                    val current = bestEntryFor[animeId]
                    if (current == null || score > current.score) {
                        bestEntryFor[animeId] = MalTitleMatcher.Match(entry, entry.title, score)
                    }
                }
            }

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
                for (track in linked) {
                    if (entry.progress > track.lastChapterRead) {
                        applyMangaProgress(track.mangaId, entry.progress)
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
            val bestEntryFor = mutableMapOf<Long, MalTitleMatcher.Match<MalListScreenModel.MalListEntry>>()
            for (entry in entries) {
                val romaji = anilistTitles[entry.id.toInt()]?.titleRomaji.orEmpty()
                for ((mangaId, mangaTitle) in unlinkedManga) {
                    val score = maxOf(
                        MalTitleMatcher.score(entry.title, mangaTitle),
                        MalTitleMatcher.score(romaji, mangaTitle),
                    )
                    if (score < MalTitleMatcher.MIN_SCORE) continue
                    val current = bestEntryFor[mangaId]
                    if (current == null || score > current.score) {
                        bestEntryFor[mangaId] = MalTitleMatcher.Match(entry, entry.title, score)
                    }
                }
            }

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
    suspend fun applyProgressForAnime(animeId: Long, malId: Long): Int? {
        val progress = progressOfAnime(malId) ?: return null
        applyAnimeProgress(animeId, progress)
        return progress
    }

    /**
     * Applies the progress of the MyAnimeList entry of the manga to the local chapters.
     */
    suspend fun applyProgressForManga(mangaId: Long, malId: Long): Int? {
        val progress = progressOfManga(malId) ?: return null
        applyMangaProgress(mangaId, progress)
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
    suspend fun applyAnimeProgress(animeId: Long, progress: Int) {
        if (progress <= 0) return
        val episodes = getEpisodes.await(animeId)
        if (episodes.isEmpty()) {
            logEvent("Anime $animeId has no episodes yet, $progress could not be ticked")
            return
        }

        // The episodes that are within the progress
        var toUpdate = episodes.filter { !it.seen && it.episodeNumber >= 1.0 && it.episodeNumber <= progress }

        // Extensions that number their episodes differently (offset or absolute numbering) don't
        // line up with the count of the list, so the first episodes are taken in that case.
        if (toUpdate.size < progress) {
            val ordered = episodes.sortedBy { it.episodeNumber }
            toUpdate = ordered.take(progress).filter { !it.seen }
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
        if (chapters.isEmpty()) {
            logEvent("Manga $mangaId has no chapters yet, $progress could not be ticked")
            return
        }

        // The chapters that are within the progress
        var toUpdate = chapters.filter { !it.read && it.chapterNumber >= 1.0 && it.chapterNumber <= progress }

        // Extensions that number their chapters differently don't line up with the count of the
        // list, so the first chapters are taken in that case.
        if (toUpdate.size < progress) {
            val ordered = chapters.sortedBy { it.chapterNumber }
            toUpdate = ordered.take(progress).filter { !it.read }
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
