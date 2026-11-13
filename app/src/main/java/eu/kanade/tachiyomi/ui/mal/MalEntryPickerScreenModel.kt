package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * An entry the user can pick from.
 */
data class MalEntryChoice(
    val malId: Int?,
    val anilistId: Int?,
    val title: String,
    val coverUrl: String = "",
    val format: String? = null,
    val episodes: Int? = null,
    /** Whether the entry is already on the list of the user. */
    val onYourList: Boolean = false,
)

/**
 * Lets the user pick the MyAnimeList entry of an anime.
 *
 * The app looks the entry up on its own, but it can't always tell the entries apart - the
 * seasons of a show look almost the same and some extensions use other titles. Instead of
 * opening an entry that could be a different anime, the user picks the right one here.
 */
class MalEntryPickerScreenModel(
    private val initialQuery: String,
    private val isManga: Boolean = false,
    private val localAnimeId: Long? = null,
    private val anilistApi: AnilistBrowseApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList,
    private val getEpisodes: GetEpisodesByAnimeId = Injekt.get(),
) : StateScreenModel<MalEntryPickerScreenModel.State>(State(query = initialQuery)) {

    private var lastQuery = ""

    init {
        if (initialQuery.isNotBlank()) {
            search(initialQuery)
        }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    /**
     * Searches for the entries that fit the query.
     */
    fun search(query: String = mutableState.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        if (trimmed != mutableState.value.query) {
            mutableState.update { it.copy(query = trimmed) }
        }
        if (trimmed == lastQuery && mutableState.value.searched) return
        lastQuery = trimmed
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val items = searchEntries(trimmed)
                mutableState.update {
                    it.copy(
                        loading = false,
                        items = items.toImmutableList(),
                        searched = true,
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /**
     * The entries of the list of the user, of MyAnimeList and of AniList that fit the query.
     *
     * The entries of the user's own list come first - they are what the user is looking for.
     * The ones that are already on the list are marked, so the right one is easy to spot.
     */
    private suspend fun searchEntries(query: String): List<MalEntryChoice> {
        val choices = LinkedHashMap<Int, MalEntryChoice>()
        val onYourList = mutableSetOf<Int>()

        // The list of the user - it is exactly what they are looking for, and it is known
        // without a request.
        val mediaType = if (isManga) {
            MalListScreenModel.MediaType.MANGA
        } else {
            MalListScreenModel.MediaType.ANIME
        }
        MalListStore.read(mediaType)?.forEach { entry ->
            val id = entry.id.toInt()
            onYourList += id
            choices[id] = MalEntryChoice(
                malId = id,
                anilistId = null,
                title = entry.title,
                coverUrl = entry.coverUrl,
                format = entry.mediaType,
                episodes = entry.total,
                onYourList = true,
            )
        }

        // AniList knows the mapping between both and has the covers
        if (!isManga) {
            try {
                val page = anilistApi.searchAnime(searchQuery(query), includeAdult = true)
                for (item in page.items) {
                    val key = item.malId ?: item.anilistId
                    val existing = choices[key]
                    choices[key] = MalEntryChoice(
                        malId = item.malId,
                        anilistId = item.anilistId,
                        title = item.title,
                        coverUrl = item.coverUrl,
                        format = item.format,
                        episodes = item.episodes ?: existing?.episodes,
                        onYourList = existing?.onYourList == true,
                    )
                }
            } catch (e: Exception) {
                // AniList is only a bonus, MyAnimeList is the one that matters
            }
        }

        // MyAnimeList knows the entries AniList doesn't have
        if (tracker.isLoggedIn) {
            try {
                val results = if (isManga) {
                    tracker.searchMangaTitles(searchQuery(query))
                } else {
                    tracker.searchAnimeTitles(searchQuery(query))
                }
                val details = coroutineScope {
                    results.take(MAX_DETAIL_REQUESTS).map { (id, title) ->
                        async { id to (if (isManga) null else tracker.getAnimeDetails(id)) }
                    }.awaitAll()
                }
                for ((id, detail) in details) {
                    val existing = choices[id]
                    choices[id] = MalEntryChoice(
                        malId = id,
                        anilistId = existing?.anilistId,
                        title = detail?.title?.takeIf { it.isNotBlank() } ?: existing?.title.orEmpty(),
                        coverUrl = detail?.cover_url?.takeIf { it.isNotBlank() } ?: existing?.coverUrl.orEmpty(),
                        format = detail?.publishing_type?.takeIf { it.isNotBlank() } ?: existing?.format,
                        episodes = detail?.total_episodes?.toInt()?.takeIf { it > 0 } ?: existing?.episodes,
                        onYourList = existing?.onYourList == true,
                    )
                }
            } catch (e: Exception) {
                // The list of MyAnimeList is not reachable, the other results are shown
            }
        }

        return rank(choices.values.toList(), query)
    }

    /**
     * The bare title - the decorations the extensions add only confuse the search engines.
     */
    private fun searchQuery(query: String): String =
        MalTitleMatcher.clean(query).takeIf { it.isNotBlank() } ?: query

    /**
     * The order the entries are shown in.
     *
     * The entries of the user's own list come first, then the ones whose title fits the
     * anime, then the ones with the same number of episodes - the seasons of a show have
     * nearly the same title, but not the same number of episodes.
     */
    private suspend fun rank(choices: List<MalEntryChoice>, query: String): List<MalEntryChoice> {
        val localEpisodes = if (isManga) null else localEpisodeCount()
        return choices
            .map { choice -> choice to score(choice, query, localEpisodes) }
            .sortedWith(
                compareByDescending<Pair<MalEntryChoice, Int>> { it.first.onYourList }
                    .thenByDescending { it.second }
                    .thenBy { it.first.title },
            )
            .map { it.first }
    }

    /**
     * How well the entry fits the anime.
     */
    private fun score(choice: MalEntryChoice, query: String, localEpisodes: Int?): Int {
        var score = MalTitleMatcher.score(query, choice.title)

        // The number of episodes tells the seasons of a show apart - the titles of those are
        // nearly the same, but the seasons have their own number of episodes.
        val episodes = choice.episodes
        if (localEpisodes != null && localEpisodes > 0 && episodes != null && episodes > 0) {
            val difference = kotlin.math.abs(localEpisodes - episodes)
            score += when {
                difference == 0 -> 20
                difference <= 2 -> 10
                difference <= 10 -> 0
                else -> -10
            }
        }

        return score
    }

    /**
     * The number of episodes the local anime has.
     */
    private suspend fun localEpisodeCount(): Int? {
        val animeId = localAnimeId ?: return null
        return try {
            getEpisodes.await(animeId).size.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    data class State(
        val query: String = "",
        val loading: Boolean = false,
        val error: Boolean = false,
        val searched: Boolean = false,
        val items: ImmutableList<MalEntryChoice> = persistentListOf(),
    )

    companion object {
        /**
         * How many entries get their details loaded - the cover and the episode count.
         */
        private const val MAX_DETAIL_REQUESTS = 8
    }
}
