package eu.kanade.tachiyomi.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.player.ExternalPlayerPickerDialog
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import kotlinx.collections.immutable.persistentListOf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

data object DownloadsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_downloads),
                icon = painterResource(R.drawable.ic_download_item_24dp),
            )
        }

    @Composable
    override fun Content() {
        var isAnime by rememberSaveable { mutableStateOf(true) }
        val screenModel = rememberScreenModel { DownloadsListScreenModel() }
        val state by screenModel.state.collectAsState()
        val animeQueue by screenModel.animeQueue.collectAsState()
        val mangaQueue by screenModel.mangaQueue.collectAsState()
        val isAnimeRunning by screenModel.isAnimeDownloaderRunning.collectAsState()
        val isMangaRunning by screenModel.isMangaDownloaderRunning.collectAsState()

        val activeCount = if (isAnime) animeQueue.size else mangaQueue.size
        val isRunning = if (isAnime) isAnimeRunning else isMangaRunning

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var showPlayerPicker by remember { mutableStateOf(false) }
        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(MR.strings.label_downloads),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, false),
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeCount > 0) {
                                val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
                                Pill(
                                    text = "$activeCount",
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = MaterialTheme.colorScheme.onBackground
                                        .copy(alpha = pillAlpha),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    },
                    actions = {
                        // Lets the user change the player used for the downloaded videos
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(AYMR.strings.pref_external_player_preference),
                                    icon = Icons.Outlined.Movie,
                                    onClick = { showPlayerPicker = true },
                                ),
                            ),
                        )
                        if (activeCount > 0) {
                            QueueActions(
                                isAnime = isAnime,
                                onSort = { mode ->
                                    if (isAnime) {
                                        screenModel.sortAnimeQueue(mode)
                                    } else {
                                        screenModel.sortMangaQueue(mode)
                                    }
                                },
                                onCancelAll = {
                                    if (isAnime) {
                                        screenModel.clearAnimeQueue()
                                    } else {
                                        screenModel.clearMangaQueue()
                                    }
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = activeCount > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val id = if (isRunning) {
                                if (isAnime) AYMR.strings.action_stop else MR.strings.action_pause
                            } else {
                                if (isAnime) AYMR.strings.action_continue else MR.strings.action_resume
                            }
                            Text(text = stringResource(id))
                        },
                        icon = {
                            Icon(
                                imageVector = if (isRunning) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (isRunning) {
                                if (isAnime) screenModel.pauseAnimeDownloads() else screenModel.pauseMangaDownloads()
                            } else {
                                if (isAnime) screenModel.startAnimeDownloads() else screenModel.startMangaDownloads()
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier.padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
            ) {
                DownloadsPage(
                    screenModel = screenModel,
                    state = state,
                    animeQueue = animeQueue,
                    mangaQueue = mangaQueue,
                    isAnime = isAnime,
                    onIsAnimeChange = { isAnime = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showPlayerPicker) {
            // Show the player that is actually used: the external one is only in charge when
            // the preference is enabled, otherwise the built-in player plays the downloads.
            val useExternalPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            ExternalPlayerPickerDialog(
                selectedPackage = playerPreferences.externalPlayerPreference().get()
                    .takeIf { it.isNotEmpty() && useExternalPlayer },
                onSelect = { packageName ->
                    if (packageName == null) {
                        // Use the built-in player
                        playerPreferences.alwaysUseExternalPlayer().set(false)
                    } else {
                        playerPreferences.alwaysUseExternalPlayer().set(true)
                        playerPreferences.externalPlayerPreference().set(packageName)
                    }
                    showPlayerPicker = false
                },
                onDismissRequest = { showPlayerPicker = false },
            )
        }
    }

    @Composable
    private fun QueueActions(
        isAnime: Boolean,
        onSort: (QueueSort) -> Unit,
        onCancelAll: () -> Unit,
    ) {
        var sortExpanded by remember { mutableStateOf(false) }
        val onDismissRequest = { sortExpanded = false }
        DropdownMenu(
            expanded = sortExpanded,
            onDismissRequest = onDismissRequest,
        ) {
            NestedMenuItem(
                text = { Text(text = stringResource(MR.strings.action_order_by_upload_date)) },
                children = { closeMenu ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_newest)) },
                        onClick = {
                            onSort(QueueSort.NewestUpload)
                            closeMenu()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_oldest)) },
                        onClick = {
                            onSort(QueueSort.OldestUpload)
                            closeMenu()
                        },
                    )
                },
            )
            NestedMenuItem(
                text = {
                    Text(
                        text = stringResource(
                            if (isAnime) {
                                AYMR.strings.action_order_by_episode_number
                            } else {
                                MR.strings.action_order_by_chapter_number
                            },
                        ),
                    )
                },
                children = { closeMenu ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_asc)) },
                        onClick = {
                            onSort(QueueSort.NumberAscending)
                            closeMenu()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_desc)) },
                        onClick = {
                            onSort(QueueSort.NumberDescending)
                            closeMenu()
                        },
                    )
                },
            )
        }

        AppBarActions(
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_sort),
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    onClick = { sortExpanded = true },
                ),
                AppBar.OverflowAction(
                    title = stringResource(MR.strings.action_cancel_all),
                    onClick = onCancelAll,
                ),
            ),
        )
    }
}
