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
 * The result of a lookup.
 *
 * When the app can't tell the entries apart, it doesn't guess - the user picks the entry
 * instead. Opening the wrong anime is worse than asking.
 */
sealed interface MalEntryLookup {
    /** The entry was found and the app is sure about it. */
    data class Found(val ids: MalEntryIds) : MalEntryLookup

    /** Nothing fits the title, or several entries fit it equally well. */
    data object Uncertain : MalEntryLookup

    /** The user isn't signed in to MyAnimeList. */
    data object NotSignedIn : MalEntryLookup
}

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
     * Looks the entry of an anime up, without guessing.
     *
     * The app only opens an entry when the title is exactly the same - the seasons of a show
     * look almost the same, and "One Piece" is not "One Piece Shippuden". When the title is
     * not an exact match, [MalEntryLookup.Uncertain] is returned and the user picks the entry.
     */
    suspend fun lookupAnime(
        animeId: Long,
        title: String,
        tracks: List<AnimeTrack>,
    ): MalEntryLookup {
        val malId = tracks.find { it.trackerId == MAL_TRACKER_ID }?.remoteId?.toInt()
        val anilistId = tracks.find { it.trackerId == ANILIST_TRACKER_ID }?.remoteId?.toInt()
        if (malId != null || anilistId != null) {
            return MalEntryLookup.Found(MalEntryIds(anilistId = anilistId, malId = malId))
        }
        if (!Injekt.get<TrackerManager>().myAnimeList.isLoggedIn) {
            return MalEntryLookup.NotSignedIn
        }
        val ids = resolve(title, MalListScreenModel.MediaType.ANIME)
            ?: return MalEntryLookup.Uncertain
        return MalEntryLookup.Found(ids)
    }

    /**
     * The same for a manga.
     */
    suspend fun lookupManga(
        mangaId: Long,
        title: String,
        tracks: List<MangaTrack>,
    ): MalEntryLookup {
        val malId = tracks.find { it.trackerId == MAL_TRACKER_ID }?.remoteId?.toInt()
        val anilistId = tracks.find { it.trackerId == ANILIST_TRACKER_ID }?.remoteId?.toInt()
        if (malId != null || anilistId != null) {
            return MalEntryLookup.Found(MalEntryIds(anilistId = anilistId, malId = malId))
        }
        if (!Injekt.get<TrackerManager>().myAnimeList.isLoggedIn) {
            return MalEntryLookup.NotSignedIn
        }
        val ids = resolve(title, MalListScreenModel.MediaType.MANGA)
            ?: return MalEntryLookup.Uncertain
        return MalEntryLookup.Found(ids)
    }

    /**
     * Finds the MyAnimeList id of the entry with the given title.
     *
     * Only an exact title match is accepted. The extensions use other titles than MyAnimeList
     * ("Sousou no Frieren" / "Frieren: Beyond Journey's End"), so the romaji title of the
     * AniList entry counts as well - it is the same title in another language.
     *
     * Everything else is handed over to the user: the seasons of a show look almost the same
     * and "One Piece" is not "One Piece Shippuden". A wrong entry is worse than a question.
     */
    private suspend fun resolve(
        title: String,
        mediaType: MalListScreenModel.MediaType,
    ): MalEntryIds? {
        if (title.isBlank()) return null

        val candidates = mutableListOf<Candidate>()

        // The extensions decorate their titles ("[Sub] Frieren (TV)") - the search engines
        // understand the bare title much better.
        val searchQuery = MalTitleMatcher.clean(title).takeIf { it.isNotBlank() } ?: title

        // The user's own list - it is exactly what they are looking at
        val cached = MalListStore.read(mediaType)
        cached?.forEach { entry ->
            candidates += Candidate(malId = entry.id.toInt(), anilistId = null, title = entry.title)
        }

        // AniList knows the mapping between both
        val anilistResults = searchByAnilist(searchQuery)
        candidates += anilistResults

        // MyAnimeList knows the entries AniList doesn't have
        searchByMal(searchQuery, mediaType)?.forEach { (id, entryTitle) ->
            candidates += Candidate(malId = id, anilistId = null, title = entryTitle)
        }

        // The exact matches - the title of the anime is the title of the entry.
        val exact = candidates.filter { candidate ->
            candidate.titles().any { candidateTitle ->
                MalTitleMatcher.isSameTitle(candidateTitle, title) ||
                    MalTitleMatcher.isSameTitle(candidateTitle, MalTitleMatcher.clean(title))
            }
        }
        if (exact.isEmpty()) {
            MalProgressSync.logEvent("No exact MyAnimeList match for '$title'")
            return null
        }

        // Several entries with exactly the same title - the user has to say which one they
        // mean, the app must not pick one on its own.
        val distinctMalIds = exact.mapNotNull { it.malId }.distinct()
        if (distinctMalIds.size > 1) {
            MalProgressSync.logEvent("Several MyAnimeList entries are called '$title'")
            return null
        }

        // The candidate that knows the most: the AniList one carries the AniList id as well.
        val best = exact.firstOrNull { it.anilistId != null && it.malId != null }
            ?: exact.firstOrNull { it.malId != null }
            ?: exact.first()
        MalProgressSync.logEvent(
            "Matched '$title' with MyAnimeList entry ${best.malId} ('${best.title}')",
        )
        return MalEntryIds(anilistId = best.anilistId, malId = best.malId)
    }

    /**
     * The titles the entry is known under.
     */
    private fun Candidate.titles(): List<String> = listOf(title, titleRomaji)
        .filter { it.isNotBlank() }
        .flatMap { listOf(it, MalTitleMatcher.clean(it)) }
        .filter { it.isNotBlank() }
        .distinct()

    /**
     * Searches AniList, which knows the MyAnimeList id of the anime it knows.
     */
    private suspend fun searchByAnilist(title: String): List<Candidate> {
        return try {
            val api = AnilistBrowseApi(Injekt.get<NetworkHelper>().client)
            val result = withTimeout(8.seconds) { api.searchAnime(title, includeAdult = true) }
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
