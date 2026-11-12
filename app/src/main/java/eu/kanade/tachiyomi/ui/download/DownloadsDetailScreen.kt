package eu.kanade.tachiyomi.ui.download

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DownloadsDetailScreen(
    private val animeId: Long? = null,
    private val mangaId: Long? = null,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { DownloadsDetailScreenModel(animeId, mangaId) }
        val state by screenModel.state.collectAsState()

        var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        val selectionMode = selectedIds.isNotEmpty()

        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

        val selectedItems = state.items.filter { it.id in selectedIds }

        fun playItem(item: DownloadsDetailItem) {
            scope.launch {
                when (item) {
                    is DownloadsDetailItem.Episode -> {
                        MainActivity.startPlayerActivity(
                            context = context,
                            animeId = animeId!!,
                            episodeId = item.id,
                            extPlayer = playerPreferences.alwaysUseExternalPlayer().get(),
                        )
                    }
                    is DownloadsDetailItem.Chapter -> {
                        context.startActivity(
                            ReaderActivity.newIntent(context, mangaId, item.id),
                        )
                    }
                }
            }
        }

        fun playItemExternally(item: DownloadsDetailItem) {
            scope.launch {
                when (item) {
                    is DownloadsDetailItem.Episode -> {
                        MainActivity.startPlayerActivity(
                            context = context,
                            animeId = animeId!!,
                            episodeId = item.id,
                            extPlayer = true,
                            allowExternalForOnline = true,
                        )
                    }
                    is DownloadsDetailItem.Chapter -> Unit
                }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Text(
                            text = if (selectionMode) {
                                stringResource(MR.strings.action_selected_count, selectedIds.size)
                            } else {
                                state.title
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        if (selectionMode) {
                            AppBarActions(
                                if (state.isAnime) {
                                    persistentListOf(
                                        AppBar.Action(
                                            title = stringResource(AYMR.strings.action_play),
                                            icon = Icons.Filled.PlayArrow,
                                            onClick = {
                                                selectedItems.firstOrNull()?.let(::playItem)
                                            },
                                        ),
                                        // Second player for the downloaded episodes, e.g. VLC
                                        AppBar.Action(
                                            title = stringResource(AYMR.strings.action_play_externally),
                                            icon = Icons.Outlined.OpenInNew,
                                            onClick = {
                                                selectedItems.firstOrNull()?.let(::playItemExternally)
                                            },
                                        ),
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_delete),
                                            icon = Icons.Filled.Delete,
                                            onClick = { showDeleteDialog = true },
                                        ),
                                    )
                                } else {
                                    persistentListOf(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.label_read),
                                            icon = Icons.AutoMirrored.Filled.MenuBook,
                                            onClick = {
                                                selectedItems.firstOrNull()?.let(::playItem)
                                            },
                                        ),
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_delete),
                                            icon = Icons.Filled.Delete,
                                            onClick = { showDeleteDialog = true },
                                        ),
                                    )
                                },
                            )
                        } else {
                            // Jump to the entry's page in its source extension to grab more episodes
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_open_in_source),
                                        icon = Icons.Outlined.OpenInNew,
                                        onClick = {
                                            if (animeId != null) {
                                                navigator.push(AnimeScreen(animeId, fromSource = true))
                                            } else if (mangaId != null) {
                                                navigator.push(MangaScreen(mangaId, fromSource = true))
                                            }
                                        },
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!selectionMode && state.items.isNotEmpty()) {
                    val next = state.items.firstOrNull { !it.watched }
                    ExtendedFloatingActionButton(
                        text = {
                            Text(
                                text = stringResource(
                                    if (state.isAnime) AYMR.strings.action_continue else MR.strings.action_continue_reading,
                                ),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = if (state.isAnime) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.AutoMirrored.Filled.MenuBook
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            (next ?: state.items.firstOrNull())?.let(::playItem)
                        },
                    )
                }
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen()
                state.items.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.no_downloads,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> ScrollbarLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.cover != null) {
                                if (state.isAnime) {
                                    ItemCover.Thumb(
                                        data = state.cover,
                                        modifier = Modifier.size(48.dp, 48.dp * 16f / 9f),
                                    )
                                } else {
                                    ItemCover.Book(
                                        data = state.cover,
                                        modifier = Modifier.size(36.dp, 48.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                )
                                Text(
                                    text = stringResource(
                                        if (state.isAnime) {
                                            MR.strings.downloaded_episodes_count
                                        } else {
                                            MR.strings.downloaded_chapters_count
                                        },
                                        state.items.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    items(
                        items = state.items,
                        key = { it.id },
                        contentType = { it::class },
                    ) { item ->
                        DownloadsDetailRow(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = selectedIds.toggle(item.id)
                                } else {
                                    playItem(item)
                                }
                            },
                            onLongClick = {
                                selectedIds = selectedIds.toggle(item.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(
                        text = stringResource(MR.strings.action_selected_count, selectedIds.size),
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            if (state.isAnime) {
                                AYMR.strings.delete_downloads_for_anime
                            } else {
                                MR.strings.delete_downloads_for_manga
                            },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.deleteSelected(selectedItems)
                            selectedIds = emptySet()
                            showDeleteDialog = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> {
    return if (id in this) this - id else this + id
}

@Composable
private fun DownloadsDetailRow(
    item: DownloadsDetailItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (item.number % 1.0 == 0.0) {
                    item.number.toInt().toString()
                } else {
                    item.number.toString()
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Text(
                text = buildString {
                    append(Formatter.formatFileSize(context, item.sizeBytes))
                    if (item.watched) {
                        append(" • ")
                        append(
                            stringResource(
                                if (item is DownloadsDetailItem.Episode) {
                                    MR.strings.label_seen
                                } else {
                                    MR.strings.label_read
                                },
                            ),
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = if (item is DownloadsDetailItem.Episode) {
                Icons.Filled.PlayArrow
            } else {
                Icons.AutoMirrored.Filled.MenuBook
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
