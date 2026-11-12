package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseItem
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

/**
 * Backs the Season and Top sections of the MAL tab.
 * Anonymous data via AniList's GraphQL API (no sign-in required).
 *
 * Supports paging through results ("Show more" button). The Season source
 * allows browsing any past/present AniList season via [setSeason].
 */
class MalBrowseScreenModel(
    initialSource: Source,
    private val anilistApi: AnilistBrowseApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
) : StateScreenModel<MalBrowseScreenModel.State>(
    State(
        source = initialSource,
        season = currentSeason(),
        seasonYear = currentSeasonYear(),
    ),
) {

    enum class Source { SEASONAL, TOP_SCORE, TOP_POPULAR, TOP_AIRING }

    private var apiSource = initialSource

    init {
        load(append = false)
    }

    fun setSource(source: Source) {
        if (source == apiSource) return
        apiSource = source
        mutableState.update { it.copy(source = source) }
        load(append = false)
    }

    fun setSeason(season: String, seasonYear: Int) {
        mutableState.update { it.copy(season = season, seasonYear = seasonYear) }
        load(append = false)
    }

    fun load(append: Boolean) {
        val source = apiSource
        val current = mutableState.value
        val page = if (append) current.page + 1 else 1
        screenModelScope.launchIO {
            mutableState.update {
                it.copy(
                    loading = !append,
                    loadingMore = append,
                    error = false,
                )
            }
            try {
                val result = when (source) {
                    Source.SEASONAL -> anilistApi.seasonal(current.season, current.seasonYear, page)
                    Source.TOP_SCORE -> anilistApi.topAnime(AnilistBrowseApi.TopFilter.BY_SCORE, page)
                    Source.TOP_POPULAR -> anilistApi.topAnime(AnilistBrowseApi.TopFilter.BY_POPULARITY, page)
                    Source.TOP_AIRING -> anilistApi.topAnime(AnilistBrowseApi.TopFilter.AIRING, page)
                }
                mutableState.update { state ->
                    state.copy(
                        loading = false,
                        loadingMore = false,
                        page = page,
                        hasNextPage = result.hasNextPage,
                        items = if (append) {
                            (state.items + result.items).toImmutableList()
                        } else {
                            result.items.toImmutableList()
                        },
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, loadingMore = false, error = true) }
            }
        }
    }

    data class State(
        val source: Source = Source.SEASONAL,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: Boolean = false,
        val items: ImmutableList<AnilistBrowseItem> = persistentListOf(),
        val page: Int = 1,
        val hasNextPage: Boolean = false,
        val season: String = "FALL",
        val seasonYear: Int = 2026,
    )

    companion object {
        fun currentSeason(): String {
            return when (LocalDate.now().monthValue) {
                12, 1, 2 -> "WINTER"
                3, 4, 5 -> "SPRING"
                6, 7, 8 -> "SUMMER"
                else -> "FALL"
            }
        }

        fun currentSeasonYear(): Int {
            val now = LocalDate.now()
            return if (now.monthValue == 12) now.year + 1 else now.year
        }
    }
}
