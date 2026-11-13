package eu.kanade.tachiyomi.ui.mal

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

/**
 * Linking local anime and manga with their MyAnimeList entry - only by a letter for letter
 * match.
 *
 * The extension title (its decorations stripped) has to be the same sequence of words as the
 * title of the entry, and the seasons of a show stay separate entries - "One Piece 3rd Season"
 * only ever links to the season 3 entry. Everything else is left for the user in the picker.
 */
object MalEntryLinker {

    private const val ANILIST_TIMEOUT = 8

    private val tracker = Injekt.get<TrackerManager>().myAnimeList
    private val anilistApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client)

    /**
     * An entry that is a match of the anime, whenever the user's own list has it.
     */
    data class Pick(
        val malId: Int,
        /** The number of episodes watched, when the user's own list has the entry. */
        val episodesWatched: Int = 0,
        val totalEpisodes: Long = 0L,
        val statusId: Long? = null,
    )

    /**
     * The exact match of [title] in [mediaType], or null when there is none.
     *
     * A match means the same title letter for letter (the season number is part of the title).
     * Two entries that both match mean ambiguity - null, and the user picks.
     */
    suspend fun findExact(title: String, mediaType: MalListScreenModel.MediaType): Pick? {
        val cleaned = MalTitleMatcher.clean(title)
        if (cleaned.isBlank()) return null

        // Every title an entry is known under - the romaji one is what the extensions use
        val titlesOf = LinkedHashMap<Int, MutableSet<String>>()
        val ownList = HashMap<Int, MalListScreenModel.MalListEntry>()

        // The list of the user - known without a request, and it is where their entries are
        MalListStore.read(mediaType)?.forEach { entry ->
            titlesOf.getOrPut(entry.id.toInt()) { mutableSetOf() }.add(entry.title)
            ownList[entry.id.toInt()] = entry
        }

        // MyAnimeList's own search - the entries that are not on the list
        try {
            val results = if (mediaType == MalListScreenModel.MediaType.ANIME) {
                tracker.searchAnimeTitles(cleaned)
            } else {
                tracker.searchMangaTitles(cleaned)
            }
            for ((id, entryTitle) in results) {
                titlesOf.getOrPut(id) { mutableSetOf() }.add(entryTitle)
            }
        } catch (e: Exception) {
            // MyAnimeList is not reachable - the list is enough, the rest is the picker
        }

        // AniList knows the mapping between both for anime
        if (mediaType == MalListScreenModel.MediaType.ANIME) {
            for ((id, titles) in searchByAnilist(cleaned)) {
                titlesOf.getOrPut(id) { mutableSetOf() }.addAll(titles)
            }
        }

        val matches = titlesOf.filterValues { knownTitles ->
            knownTitles.any { MalTitleMatcher.isSameTitle(it, cleaned) }
        }
        val ids = matches.keys
        if (ids.size != 1) return null

        val malId = ids.first()
        val own = ownList[malId]
        return Pick(
            malId = malId,
            episodesWatched = own?.progress ?: 0,
            totalEpisodes = own?.total?.toLong() ?: 0L,
            statusId = own?.status?.toStatusId(),
        )
    }

    /**
     * Whether the entry the app has linked with the anime is the same anime as the local one.
     *
     * A link the app cannot verify is not a link it may trust - the seasons of a show look
     * almost the same, and an unverifiable link could point anywhere.
     */
    suspend fun linkedEntryMatches(title: String, malId: Int?, anilistId: Int?, mediaType: MalListScreenModel.MediaType): Boolean {
        val cleaned = MalTitleMatcher.clean(title)
        if (cleaned.isBlank()) return false

        val linkedTitles = linkedTitles(malId, anilistId, mediaType)
        if (linkedTitles.isEmpty()) return false
        return linkedTitles.any { MalTitleMatcher.isSameTitle(it, cleaned) }
    }

    private suspend fun linkedTitles(malId: Int?, anilistId: Int?, mediaType: MalListScreenModel.MediaType): List<String> {
        if (malId != null) {
            val own = MalListStore.read(mediaType)?.firstOrNull { it.id.toInt() == malId }?.title
            if (own != null) return listOf(own)
            if (mediaType == MalListScreenModel.MediaType.ANIME) {
                return runCatching { listOfNotNull(tracker.getAnimeDetails(malId)?.title) }
                    .getOrDefault(emptyList())
            }
            return emptyList()
        }
        if (anilistId != null) {
            return runCatching {
                anilistApi.animeDetails(anilistId = anilistId, malId = null).let { details ->
                    listOfNotNull(
                        details.titleRomaji.takeIf { it.isNotBlank() },
                        details.titleEnglish?.takeIf { it.isNotBlank() },
                        details.titleNative?.takeIf { it.isNotBlank() },
                    )
                }
            }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    /**
     * The titles an AniList entry is known under.
     */
    private suspend fun searchByAnilist(query: String): Map<Int, List<String>> {
        val result = try {
            withTimeout(ANILIST_TIMEOUT.seconds) { anilistApi.searchAnime(query, includeAdult = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyMap()
        }
        return result.items
            .filter { it.malId != null }
            .associate { item ->
                item.malId!! to listOfNotNull(
                    item.title.takeIf { it.isNotBlank() },
                    item.titleRomaji.takeIf { it.isNotBlank() },
                )
            }
    }

    /**
     * The MyAnimeList status into the id the tracker uses.
     */
    private fun String?.toStatusId(): Long =
        when (this) {
            "watching" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.WATCHING
            "reading" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.READING
            "completed" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.COMPLETED
            "on_hold" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.ON_HOLD
            "dropped" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.DROPPED
            "plan_to_watch" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.PLAN_TO_WATCH
            "plan_to_read" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.PLAN_TO_READ
            "rewatching" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.REWATCHING
            "rereading" -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.REREADING
            else -> eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList.PLAN_TO_WATCH
        }
}
