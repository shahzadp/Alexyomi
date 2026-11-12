package eu.kanade.tachiyomi.ui.download

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun DownloadsPage(
    screenModel: DownloadsListScreenModel,
    state: DownloadsListState,
    animeQueue: List<AnimeDownload>,
    mangaQueue: List<MangaDownload>,
    isAnime: Boolean,
    onIsAnimeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    var pendingDelete by remember { mutableStateOf<DownloadsEntry?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = isAnime,
                    onClick = { onIsAnimeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(text = stringResource(AYMR.strings.label_anime))
                }
                SegmentedButton(
                    selected = !isAnime,
                    onClick = { onIsAnimeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(text = stringResource(AYMR.strings.manga))
                }
            }
        }

        when {
            state.isLoading -> LoadingScreen()
            else -> {
                val downloadedItems: List<DownloadsEntry> = if (isAnime) state.animeItems else state.mangaItems
                val activeCount = if (isAnime) animeQueue.size else mangaQueue.size

                if (activeCount == 0 && downloadedItems.isEmpty()) {
                    EmptyDownloads(modifier = Modifier.fillMaxSize())
                    return@Column
                }

                ScrollbarLazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (activeCount > 0) {
                        item {
                            SectionHeader(
                                text = stringResource(MR.strings.label_downloading) + " ($activeCount)",
                            )
                        }
                        if (isAnime) {
                            items(
                                items = animeQueue,
                                key = { it.episode.id },
                                contentType = { "anime_queue" },
                            ) { download ->
                                AnimeDownloadingRow(
                                    download = download,
                                    onCancel = { screenModel.cancelAnimeDownloads(listOf(download)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            items(
                                items = mangaQueue,
                                key = { it.chapter.id },
                                contentType = { "manga_queue" },
                            ) { download ->
                                MangaDownloadingRow(
                                    download = download,
                                    onCancel = { screenModel.cancelMangaDownloads(listOf(download)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (downloadedItems.isNotEmpty()) {
                        if (activeCount > 0) {
                            item {
                                SectionHeader(
                                    text = stringResource(MR.strings.label_downloaded) + " (${downloadedItems.size})",
                                )
                            }
                        }
                        items(
                            items = downloadedItems,
                            key = { it.id },
                            contentType = { it::class },
                        ) { item ->
                            DownloadsEntryRow(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is DownloadsEntry.AnimeEntry -> navigator.push(
                                            DownloadsDetailScreen(animeId = item.id),
                                        )
                                        is DownloadsEntry.MangaEntry -> navigator.push(
                                            DownloadsDetailScreen(mangaId = item.id),
                                        )
                                    }
                                },
                                onOpenInSource = {
                                    // Jump to the entry's page in its source extension to grab more episodes
                                    when (item) {
                                        is DownloadsEntry.AnimeEntry -> navigator.push(
                                            AnimeScreen(item.id, fromSource = true),
                                        )
                                        is DownloadsEntry.MangaEntry -> navigator.push(
                                            MangaScreen(item.id, fromSource = true),
                                        )
                                    }
                                },
                                onDelete = { pendingDelete = item },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = entry.title, maxLines = 1) },
            text = {
                Text(
                    text = stringResource(
                        if (entry.isAnime) {
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
                        screenModel.deleteDownloads(entry)
                        pendingDelete = null
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download_item_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .height(88.dp)
                .width(88.dp)
                .alpha(0.8f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(MR.strings.no_downloads),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadingRow(
    title: String,
    itemName: String,
    isQueued: Boolean,
    isError: Boolean,
    progress: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                isError -> Icons.Outlined.ErrorOutline
                isQueued -> Icons.Outlined.Schedule
                else -> Icons.Outlined.Download
            },
            contentDescription = null,
            tint = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.width(36.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(
                text = itemName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            when {
                isError -> Text(
                    text = stringResource(MR.strings.error),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                isQueued -> Text(
                    text = stringResource(MR.strings.status_queued),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                progress < 0 -> LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, end = 8.dp),
                )
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(MR.strings.action_cancel),
            )
        }
    }
}

@Composable
private fun AnimeDownloadingRow(
    download: AnimeDownload,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by download.progressFlow.collectAsState()
    val status by download.statusFlow.collectAsState()

    DownloadingRow(
        title = download.anime.title,
        itemName = download.episode.name,
        isQueued = status == AnimeDownload.State.QUEUE,
        isError = status == AnimeDownload.State.ERROR,
        progress = progress.coerceAtLeast(-1).coerceAtMost(100),
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
private fun MangaDownloadingRow(
    download: MangaDownload,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by download.progressFlow.collectAsState(initial = 0)
    val status by download.statusFlow.collectAsState()

    DownloadingRow(
        title = download.manga.title,
        itemName = download.chapter.name,
        isQueued = status == MangaDownload.State.QUEUE,
        isError = status == MangaDownload.State.ERROR,
        progress = progress.coerceAtLeast(-1).coerceAtMost(100),
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
private fun DownloadsEntryRow(
    item: DownloadsEntry,
    onClick: () -> Unit,
    onOpenInSource: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item) {
            is DownloadsEntry.AnimeEntry -> {
                ItemCover.Thumb(
                    data = item.cover,
                    modifier = Modifier
                        .height(54.dp)
                        .width(96.dp),
                    onClick = onClick,
                )
            }
            is DownloadsEntry.MangaEntry -> {
                ItemCover.Book(
                    data = item.cover,
                    modifier = Modifier
                        .height(81.dp)
                        .width(54.dp),
                    onClick = onClick,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            val unit = if (item.isAnime) {
                stringResource(MR.strings.downloaded_episodes_count, item.count)
            } else {
                stringResource(MR.strings.downloaded_chapters_count, item.count)
            }
            val size = Formatter.formatFileSize(context, item.sizeBytes)
            Text(
                text = "$unit • $size",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.sourceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onOpenInSource) {
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = stringResource(MR.strings.action_open_in_source),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
            )
        }
    }
}
