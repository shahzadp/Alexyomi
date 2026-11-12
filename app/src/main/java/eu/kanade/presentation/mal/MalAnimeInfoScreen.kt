package eu.kanade.presentation.mal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.mal.MalAnimeInfoScreenModel
import eu.kanade.tachiyomi.ui.mal.MalListScreenModel
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.data.track.anilist.AnilistAnimeDetails
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MalAnimeInfoScreen(
    state: MalAnimeInfoScreenModel.State,
    events: Flow<MalAnimeInfoScreenModel.Event>,
    fallbackTitle: String,
    navigateUp: () -> Unit,
    onWatch: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onRetry: () -> Unit,
    onSaveEntry: (status: String, score: Int, episodesWatched: Int) -> Unit,
    onRemoveEntry: () -> Unit,
    openEditorOnStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val details = state.details
    val title = details?.displayTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember(openEditorOnStart) { mutableStateOf(openEditorOnStart) }

    LaunchedEffect(Unit) {
        events.collectLatest { event ->
            val message = when (event) {
                MalAnimeInfoScreenModel.Event.Updated -> AYMR.strings.mal_list_entry_updated
                MalAnimeInfoScreenModel.Event.UpdateFailed -> AYMR.strings.mal_load_failed
                MalAnimeInfoScreenModel.Event.Removed -> AYMR.strings.mal_removed_from_list
            }
            snackbarHostState.showSnackbar(context.stringResource(message))
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = title,
                navigateUp = navigateUp,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            if (details != null && details.malId != null) {
                val (container, content) = statusFabColors(state.listStatus?.status)
                FloatingActionButton(
                    onClick = {
                        if (state.signedIn) {
                            editing = true
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(AYMR.strings.mal_sign_in_required),
                                )
                            }
                        }
                    },
                    containerColor = container,
                    contentColor = content,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = stringResource(AYMR.strings.mal_edit_list_entry),
                    )
                }
            }
        },
    ) { contentPadding ->
        when {
            state.loading && details == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error && details == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.mal_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
            details != null -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Banner / hero
                    details.bannerUrl?.let { banner ->
                        AsyncImage(
                            model = banner,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        ItemCover.Book(
                            data = details.coverUrl,
                            modifier = Modifier.width(104.dp),
                            contentDescription = title,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenWeb(details.webUrl) },
                        ) {
                            details.titleEnglish?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            val subtitle = buildList {
                                add(details.titleRomaji)
                                details.titleNative?.let { add(it) }
                            }.joinToString("\n")
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (details.titleEnglish != null) 2.dp else 0.dp),
                            )
                            if (details.studios.isNotEmpty()) {
                                Text(
                                    text = details.studios.joinToString(", "),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }

                    // Key facts chips
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        details.format?.let { FactChip(it) }
                        FactChip(details.episodes?.let { "$it eps" } ?: "? eps")
                        details.duration?.let { FactChip("${it}m") }
                        details.status?.let { FactChip(statusLabel(it)) }
                        seasonLabel(details)?.let { FactChip(it) }
                    }

                    // Score & popularity
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column {
                            Text(
                                text = details.averageScore?.let { "★ " + String.format(Locale.US, "%.1f", it / 10.0) } ?: "★ –",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = "Score",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Text(
                                text = details.popularity?.let { String.format(Locale.US, "%,d", it) } ?: "–",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = stringResource(AYMR.strings.mal_popularity),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        details.nextAiringEpisode?.let { ep ->
                            Column {
                                Text(
                                    text = "Ep. $ep",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    text = details.airingAt?.let {
                                        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(it * 1000))
                                    } ?: "Upcoming",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Genres
                    if (details.genres.isNotEmpty()) {
                        Text(
                            text = stringResource(AYMR.strings.mal_genres),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            details.genres.take(6).forEach { genre ->
                                FactChip(genre)
                            }
                        }
                    }

                    // Synopsis
                    if (details.description.isNotBlank()) {
                        Text(
                            text = details.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Actions: Watch resolves sources in-app; web link stays as a subtle secondary
                    Button(
                        onClick = { onWatch(title) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Text(
                            text = stringResource(AYMR.strings.mal_watch),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenWeb(details.webUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(AYMR.strings.mal_open_on_mal))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (editing && details != null && details.malId != null) {
        val airingStatus = when (details.status?.uppercase()) {
            "FINISHED" -> "finished_airing"
            "NOT_YET_RELEASED" -> "not_yet_aired"
            else -> "currently_airing"
        }
        MalEditEntrySheet(
            entry = MalListScreenModel.MalListEntry(
                id = details.malId.toLong(),
                title = title,
                coverUrl = details.coverUrl,
                progress = state.listStatus?.numEpisodesWatched?.toInt() ?: 0,
                total = details.episodes,
                status = state.listStatus?.status ?: "",
                airingStatus = airingStatus,
                isReplaying = state.listStatus?.isRewatching ?: false,
                score = state.listStatus?.score ?: 0,
                mediaType = details.format,
                seasonLabel = seasonLabel(details),
            ),
            isAnime = true,
            unit = "ep",
            onDismiss = { editing = false },
            onSave = { s, sc, ep ->
                onSaveEntry(s, sc, ep)
                editing = false
            },
            onRemove = if (state.listStatus != null) {
                {
                    onRemoveEntry()
                    editing = false
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun FactChip(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun statusLabel(status: String): String = when (status.uppercase()) {
    "RELEASING" -> "Airing"
    "FINISHED" -> "Completed"
    "NOT_YET_RELEASED" -> "Not yet released"
    "HIATUS" -> "Hiatus"
    "CANCELLED" -> "Cancelled"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

private fun seasonLabel(details: AnilistAnimeDetails): String? {
    val season = details.season?.lowercase()?.replaceFirstChar { it.uppercase() } ?: return null
    return details.seasonYear?.let { "$season $it" } ?: season
}

@Composable
private fun statusFabColors(status: String?): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val swatch = malStatusSwatch(status)
    return if (swatch != null) {
        swatch to malStatusOnSwatch(status)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }
}
