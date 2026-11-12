package eu.kanade.tachiyomi.ui.mal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.mal.MalMyListScreen
import eu.kanade.presentation.mal.MalSearchScreen
import eu.kanade.presentation.mal.MalSeasonalScreen
import eu.kanade.presentation.mal.MalTopScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object MalTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 0u,
                title = stringResource(AYMR.strings.label_mal),
                icon = painterResource(R.drawable.ic_mal_24dp),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val tracker = remember { Injekt.get<TrackerManager>().myAnimeList }
        val navigator = LocalNavigator.currentOrThrow
        val isSignedIn by tracker.isLoggedInFlow.collectAsState(initial = tracker.isLoggedIn)
        var avatarUrl by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(isSignedIn) {
            avatarUrl = if (isSignedIn) tracker.getProfilePicture() else null
        }
        val onAvatarClick = {
            if (isSignedIn) {
                navigator.push(MalProfileScreen())
            } else {
                context.openInBrowser(
                    MyAnimeListApi.authUrl().toString(),
                    forceDefaultBrowser = true,
                )
            }
        }
        val avatarAction = AppBar.AvatarAction(
            pictureUrl = avatarUrl,
            contentDescription = "MyAnimeList profile",
            onClick = onAvatarClick,
        )

        val myListScreenModel = rememberScreenModel("mal_list") { MalListScreenModel() }
        val myListState by myListScreenModel.state.collectAsState()

        TabbedScreen(
            titleRes = AYMR.strings.label_mal,
            tabs = persistentListOf(
                myListTab(myListScreenModel, myListState, avatarAction),
                seasonalTab(avatarAction),
                topTab(avatarAction),
                searchTab(avatarAction),
            ),
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }

    @Composable
    private fun myListTab(
        screenModel: MalListScreenModel,
        state: MalListScreenModel.State,
        avatarAction: AppBar.AvatarAction,
    ): TabContent {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        return TabContent(
            titleRes = AYMR.strings.mal_my_list,
            searchEnabled = false,
            actions = persistentListOf(avatarAction),
            content = { _, snackbarHostState ->
                PullToRefreshBox(
                    isRefreshing = state.loading && state.entries.isNotEmpty(),
                    onRefresh = { screenModel.load() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MalMyListScreen(
                        state = state,
                        events = screenModel.events,
                        snackbarHostState = snackbarHostState,
                        onFilterChange = screenModel::setFilter,
                        onMediaTypeChange = screenModel::setMediaType,
                        onSearchOpenChange = screenModel::setSearchOpen,
                        onSortByChange = screenModel::setSortBy,
                        onToggleSortDirection = screenModel::toggleSortDirection,
                        onQueryChange = screenModel::setQuery,
                        onIncrement = screenModel::incrementEpisode,
                        onSaveEntry = screenModel::saveEntry,
                        onSignIn = {
                            context.openInBrowser(
                                MyAnimeListApi.authUrl(),
                                forceDefaultBrowser = true,
                            )
                        },
                        onRetry = screenModel::load,
                        onRemoveEntry = screenModel::removeFromList,
                        onOpenEntry = { entry ->
                            if (state.mediaType == MalListScreenModel.MediaType.MANGA) {
                                navigator.push(
                                    MalMangaInfoScreen(
                                        anilistId = null,
                                        malId = entry.id.toInt(),
                                        fallbackTitle = entry.title,
                                    ),
                                )
                            } else {
                                navigator.push(
                                    MalAnimeInfoScreen(
                                        anilistId = null,
                                        malId = entry.id.toInt(),
                                        fallbackTitle = entry.title,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }

    @Composable
    private fun seasonalTab(avatarAction: AppBar.AvatarAction): TabContent {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel("mal_seasonal") { MalBrowseScreenModel(MalBrowseScreenModel.Source.SEASONAL) }
        val state by screenModel.state.collectAsState()
        return TabContent(
            titleRes = AYMR.strings.mal_seasonal,
            searchEnabled = false,
            actions = persistentListOf(avatarAction),
            content = { _, _ ->
                PullToRefreshBox(
                    isRefreshing = state.loading && state.items.isNotEmpty(),
                    onRefresh = { screenModel.load(append = false) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MalSeasonalScreen(
                        state = state,
                        onRetry = { screenModel.load(append = false) },
                        onItemClick = { anime ->
                            navigator.push(MalAnimeInfoScreen(anime.anilistId, anime.malId, anime.title))
                        },
                        onSeasonChange = screenModel::setSeason,
                        onShowMore = { screenModel.load(append = true) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }

    @Composable
    private fun topTab(avatarAction: AppBar.AvatarAction): TabContent {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel("mal_top") { MalBrowseScreenModel(MalBrowseScreenModel.Source.TOP_SCORE) }
        val state by screenModel.state.collectAsState()
        return TabContent(
            titleRes = AYMR.strings.mal_top,
            searchEnabled = false,
            actions = persistentListOf(avatarAction),
            content = { _, _ ->
                PullToRefreshBox(
                    isRefreshing = state.loading && state.items.isNotEmpty(),
                    onRefresh = { screenModel.load(append = false) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MalTopScreen(
                        state = state,
                        onSelectFilter = screenModel::setSource,
                        onRetry = { screenModel.load(append = false) },
                        onShowMore = { screenModel.load(append = true) },
                        onItemClick = { anime ->
                            navigator.push(
                                MalAnimeInfoScreen(anime.anilistId, anime.malId, anime.title),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }

    @Composable
    private fun searchTab(avatarAction: AppBar.AvatarAction): TabContent {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel("mal_search") { MalSearchScreenModel() }
        val state by screenModel.state.collectAsState()
        return TabContent(
            titleRes = AYMR.strings.mal_search,
            searchEnabled = false,
            actions = persistentListOf(avatarAction),
            content = { _, _ ->
                MalSearchScreen(
                    state = state,
                    onQueryChange = screenModel::setQuery,
                    onSearch = { screenModel.search() },
                    onClear = screenModel::clear,
                    onHistoryClick = { query -> screenModel.search(query) },
                    onHistoryRemove = screenModel::removeHistory,
                    onHistoryClear = screenModel::clearHistory,
                    onItemClick = { anime ->
                        navigator.push(
                            MalAnimeInfoScreen(anime.anilistId, anime.malId, anime.title),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}
