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
 * AniList alone is not enough - it knows far fewer anime than MyAnimeList and its search skips
 * the adult ones - so the lookup collects candidates from several sources and picks the one
 * whose title matches best:
 *
 * 1. the user's own MyAnimeList list (it is exactly what they are looking at)
 * 2. AniList, which knows the mapping between both and ranks its search well
 * 3. MyAnimeList's own search, which knows the entries AniList doesn't have
 */
object MalEntryResolver {

    private const val MAL_TRACKER_ID = 1L
    private const val ANILIST_TRACKER_ID = 2L

    /**
     * A possible match, with the title it is known under.
     */
    private data class Candidate(
        val malId: Int?,
        val anilistId: Int?,
        val title: String,
        /** The romaji title, the extensions usually show that one. */
        val titleRomaji: String = "",
    )

    /**
     * The best score of the candidate against the title.
     */
    private fun Candidate.scoreFor(title: String): Int {
        val byTitle = MalTitleMatcher.score(title, this.title)
        val byRomaji = if (titleRomaji.isBlank()) 0 else MalTitleMatcher.score(title, titleRomaji)
        return maxOf(byTitle, byRomaji)
    }

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
     *
     * The user's own list comes first: it is exactly what they are looking at, and it tells
     * the versions apart that share a title (a new and an old one). AniList knows the mapping
     * between both and is the fallback for the entries that are not on the list yet.
     */
    private suspend fun resolve(
        title: String,
        mediaType: MalListScreenModel.MediaType,
    ): MalEntryIds? {
        if (title.isBlank()) return null

        // The user's own list
        val cached = MalListStore.read(mediaType)
        val listMatch = cached?.let {
            MalTitleMatcher.bestMatch(title, it.map { entry -> entry.id.toInt() to entry.title })
        }
        if (listMatch != null) {
            MalProgressSync.logEvent(
                "Matched '$title' with MyAnimeList entry ${listMatch.item} " +
                    "from the list ('${listMatch.title}', score ${listMatch.score})",
            )
            return MalEntryIds(anilistId = null, malId = listMatch.item)
        }

        // AniList knows the mapping between both. Its search is ranked well, so a hit that
        // fits the title wins - and the first hit is the fallback, because the extensions
        // often show the romaji title while AniList answers with the english one.
        val anilistResults = searchByAnilist(title)
        val anilistMatch = anilistResults
            .firstOrNull { it.scoreFor(title) >= MalTitleMatcher.MIN_SCORE }
            ?: anilistResults.firstOrNull()
        if (anilistMatch != null) {
            MalProgressSync.logEvent(
                "Matched '$title' with MyAnimeList entry ${anilistMatch.malId} " +
                    "via AniList ('${anilistMatch.title}')",
            )
            return MalEntryIds(anilistId = anilistMatch.anilistId, malId = anilistMatch.malId)
        }

        // MyAnimeList knows the entries AniList doesn't have
        val malResults = searchByMal(title, mediaType)
        val malMatch = MalTitleMatcher.bestMatch(title, malResults)
        if (malMatch != null) {
            MalProgressSync.logEvent(
                "Matched '$title' with MyAnimeList entry ${malMatch.item} " +
                    "via MyAnimeList ('${malMatch.title}', score ${malMatch.score})",
            )
            return MalEntryIds(anilistId = null, malId = malMatch.item)
        }

        MalProgressSync.logEvent("No MyAnimeList match for '$title'")
        return null
    }

    /**
     * Searches AniList, which knows the MyAnimeList id of the anime it knows.
     */
    private suspend fun searchByAnilist(title: String): List<Candidate> {
        return try {
            val api = AnilistBrowseApi(Injekt.get<NetworkHelper>().client)
            val result = withTimeout(8.seconds) { api.searchAnime(title) }
            result.items.map {
                Candidate(
                    malId = it.malId,
                    anilistId = it.anilistId,
                    title = it.title,
                    titleRomaji = it.titleRomaji,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network problems must not crash the app, the caller shows a message instead
            logcat(LogPriority.ERROR, e) { "Failed to look up '$title' on AniList" }
            emptyList()
        }
    }

    /**
     * Searches MyAnimeList itself.
     */
    private suspend fun searchByMal(title: String, mediaType: MalListScreenModel.MediaType): List<Pair<Int, String>> {
        return try {
            val tracker = Injekt.get<TrackerManager>().myAnimeList
            if (!tracker.isLoggedIn) return emptyList()
            if (mediaType == MalListScreenModel.MediaType.ANIME) {
                tracker.searchAnimeTitles(title)
            } else {
                tracker.searchMangaTitles(title)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to search '$title' on MyAnimeList" }
            emptyList()
        }
    }
}
