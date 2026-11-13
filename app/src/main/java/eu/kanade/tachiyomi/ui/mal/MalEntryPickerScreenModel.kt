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
    /** Whether the entry was found on MyAnimeList itself. */
    val fromMal: Boolean = false,
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
    private val anilistApi: AnilistBrowseApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList,
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
     * The entries of both MyAnimeList and AniList that fit the query.
     *
     * AniList knows the mapping between both and has the covers, MyAnimeList knows the
     * entries AniList doesn't have.
     */
    private suspend fun searchEntries(query: String): List<MalEntryChoice> {
        val choices = LinkedHashMap<Int, MalEntryChoice>()

        if (!isManga) {
            try {
                val page = anilistApi.searchAnime(query, includeAdult = true)
                for (item in page.items) {
                    val key = item.malId ?: item.anilistId
                    choices[key] = MalEntryChoice(
                        malId = item.malId,
                        anilistId = item.anilistId,
                        title = item.title,
                        coverUrl = item.coverUrl,
                        format = item.format,
                        episodes = item.episodes,
                    )
                }
            } catch (e: Exception) {
                // AniList is only a bonus, MyAnimeList is the one that matters
            }
        }

        if (tracker.isLoggedIn) {
            try {
                val results = if (isManga) {
                    tracker.searchMangaTitles(query)
                } else {
                    tracker.searchAnimeTitles(query)
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
                        fromMal = true,
                    )
                }
            } catch (e: Exception) {
                // The list of MyAnimeList is not reachable, the AniList results are shown
            }
        }

        return choices.values.toList()
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
