package eu.kanade.tachiyomi.ui.mal

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

/**
 * Ids needed to open a MyAnimeList entry: the AniList id and/or the MAL id.
 */
data class MalEntryIds(
    val anilistId: Int?,
    val malId: Int?,
)

/**
 * Resolves the MyAnimeList/AniList ids of an entry so it can be added to (or updated on) the
 * user's MAL list.
 *
 * The AniList search alone doesn't cover everything — AniList knows far fewer anime than
 * MyAnimeList and skips the adult ones — so the lookup walks several sources:
 *
 * 1. the tracker that is already linked to the anime
 * 2. the user's own MyAnimeList list (the most reliable source, it is what the user sees)
 * 3. MyAnimeList's own search
 * 4. AniList's search, as the last resort
 */
object MalEntryResolver {

    private const val MAL_TRACKER_ID = 1L
    private const val ANILIST_TRACKER_ID = 2L

    suspend fun forAnime(animeId: Long, title: String, tracks: List<AnimeTrack>): MalEntryIds? {
        val malId = tracks.find { it.trackerId == MAL_TRACKER_ID }?.remoteId?.toInt()
        val anilistId = tracks.find { it.trackerId == ANILIST_TRACKER_ID }?.remoteId?.toInt()
        if (malId != null || anilistId != null) {
            return MalEntryIds(anilistId = anilistId, malId = malId)
        }
        return resolve(title, MalListScreenModel.MediaType.ANIME)
    }

    suspend fun forManga(mangaId: Long, title: String, tracks: List<MangaTrack>): MalEntryIds? {
        val malId = tracks.find { it.trackerId == MAL_TRACKER_ID }?.remoteId?.toInt()
        val anilistId = tracks.find { it.trackerId == ANILIST_TRACKER_ID }?.remoteId?.toInt()
        if (malId != null || anilistId != null) {
            return MalEntryIds(anilistId = anilistId, malId = malId)
        }
        return resolve(title, MalListScreenModel.MediaType.MANGA)
    }

    /**
     * Finds the MyAnimeList id of the entry with the given title.
     */
    private suspend fun resolve(
        title: String,
        mediaType: MalListScreenModel.MediaType,
    ): MalEntryIds? {
        if (title.isBlank()) return null

        // The user's own list is the best source: it is exactly what they are looking at.
        val fromList = matchInUserList(title, mediaType)
        if (fromList != null) {
            return MalEntryIds(anilistId = null, malId = fromList)
        }

        // MyAnimeList's own search knows everything that is on MyAnimeList.
        val fromMal = matchViaMalSearch(title, mediaType)
        if (fromMal != null) {
            return MalEntryIds(anilistId = null, malId = fromMal)
        }

        // AniList is the last resort, it also provides the AniList id for the details page.
        return searchByAnilist(title)
    }

    /**
     * Matches the title against the user's own MyAnimeList list.
     */
    private suspend fun matchInUserList(
        title: String,
        mediaType: MalListScreenModel.MediaType,
    ): Int? {
        val cached = MalListStore.read(mediaType) ?: return null
        val candidates = cached.map { it.id.toInt() to it.title }
        return MalTitleMatcher.best(title, candidates)?.first
    }

    /**
     * Matches the title against MyAnimeList's own search.
     */
    private suspend fun matchViaMalSearch(
        title: String,
        mediaType: MalListScreenModel.MediaType,
    ): Int? {
        return try {
            val tracker = Injekt.get<TrackerManager>().myAnimeList
            if (!tracker.isLoggedIn) return null
            val results = if (mediaType == MalListScreenModel.MediaType.ANIME) {
                tracker.searchAnimeTitles(title)
            } else {
                tracker.searchMangaTitles(title)
            }
            MalTitleMatcher.best(title, results)?.first
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to search '$title' on MyAnimeList" }
            null
        }
    }

    /**
     * Matches the title against AniList's search.
     */
    private suspend fun searchByAnilist(title: String): MalEntryIds? {
        return try {
            val api = AnilistBrowseApi(Injekt.get<NetworkHelper>().client)
            val result = withTimeout(20.seconds) { api.searchAnime(title) }
            val match = result.items.firstOrNull() ?: return null
            MalEntryIds(anilistId = match.anilistId, malId = match.malId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network problems must not crash the app, the caller shows a message instead
            logcat(LogPriority.ERROR, e) { "Failed to look up '$title' on MyAnimeList" }
            null
        }
    }
}
