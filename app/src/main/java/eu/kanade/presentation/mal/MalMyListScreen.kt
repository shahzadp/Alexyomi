package eu.kanade.presentation.mal

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.i18n.stringResource
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.mal.MalListScreenModel
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

private val MalPlusAccent = Color(0xFFA78BFA)
private val MalPlusAccentOn = Color(0xFF24103F)

private val FilterLabels = mapOf(
    MalListScreenModel.Filter.ALL to AYMR.strings.mal_chip_all,
    MalListScreenModel.Filter.WATCHING to AYMR.strings.watching,
    MalListScreenModel.Filter.PLAN_TO_WATCH to AYMR.strings.plan_to_watch,
    MalListScreenModel.Filter.COMPLETED to MR.strings.completed,
    MalListScreenModel.Filter.ON_HOLD to MR.strings.on_hold,
    MalListScreenModel.Filter.DROPPED to MR.strings.dropped,
)

private val FilterLabelsManga = mapOf(
    MalListScreenModel.Filter.ALL to AYMR.strings.mal_chip_all,
    MalListScreenModel.Filter.WATCHING to MR.strings.reading,
    MalListScreenModel.Filter.PLAN_TO_WATCH to MR.strings.plan_to_read,
    MalListScreenModel.Filter.COMPLETED to MR.strings.completed,
    MalListScreenModel.Filter.ON_HOLD to MR.strings.on_hold,
    MalListScreenModel.Filter.DROPPED to MR.strings.dropped,
)

// Short forms used on the selector button when horizontal space is tight
private val FilterLabelsShort = mapOf(
    MalListScreenModel.Filter.ALL to AYMR.strings.mal_chip_all,
    MalListScreenModel.Filter.WATCHING to AYMR.strings.mal_watch_short,
    MalListScreenModel.Filter.PLAN_TO_WATCH to AYMR.strings.mal_planned,
    MalListScreenModel.Filter.COMPLETED to AYMR.strings.mal_comp,
    MalListScreenModel.Filter.ON_HOLD to AYMR.strings.mal_hold,
    MalListScreenModel.Filter.DROPPED to AYMR.strings.mal_drop,
)

private val FilterLabelsShortManga = mapOf(
    MalListScreenModel.Filter.ALL to AYMR.strings.mal_chip_all,
    MalListScreenModel.Filter.WATCHING to AYMR.strings.mal_read_short,
    MalListScreenModel.Filter.PLAN_TO_WATCH to AYMR.strings.mal_planned,
    MalListScreenModel.Filter.COMPLETED to AYMR.strings.mal_comp,
    MalListScreenModel.Filter.ON_HOLD to AYMR.strings.mal_hold,
    MalListScreenModel.Filter.DROPPED to AYMR.strings.mal_drop,
)

private fun progressText(
    entry: MalListScreenModel.MalListEntry,
    unit: String,
): String {
    val effectiveTotal = entry.total?.takeIf { it > 0 } ?: if (entry.status == "completed") entry.progress else null
    return if (effectiveTotal != null) {
        "${entry.progress}/$effectiveTotal $unit"
    } else {
        "${entry.progress}/? $unit"
    }
}

private fun progressSuffix(
    entry: MalListScreenModel.MalListEntry,
): String {
    val effectiveTotal = entry.total?.takeIf { it > 0 } ?: if (entry.status == "completed") entry.progress else null
    return if (effectiveTotal != null) " / $effectiveTotal" else " / ?"
}

private val SortOptions = listOf(
    MalListScreenModel.Sort.STATUS,
    MalListScreenModel.Sort.ALPHABETICAL,
    MalListScreenModel.Sort.SCORE,
    MalListScreenModel.Sort.PROGRESS,
    MalListScreenModel.Sort.LAST_UPDATED,
)

private fun statusRank(status: String): Int = when (status) {
    "watching", "reading" -> 0
    "plan_to_watch", "plan_to_read" -> 1
    "on_hold" -> 2
    "dropped" -> 3
    "completed" -> 4
    else -> 5
}

private fun sortLabel(
    sort: MalListScreenModel.Sort,
    isAnime: Boolean,
): dev.icerock.moko.resources.StringResource = when (sort) {
    MalListScreenModel.Sort.STATUS -> AYMR.strings.mal_sort_status
    MalListScreenModel.Sort.ALPHABETICAL -> AYMR.strings.mal_sort_alpha
    MalListScreenModel.Sort.SCORE -> AYMR.strings.mal_sort_score
    MalListScreenModel.Sort.PROGRESS -> if (isAnime) AYMR.strings.mal_sort_episodes else AYMR.strings.mal_sort_chapters
    MalListScreenModel.Sort.LAST_UPDATED -> AYMR.strings.mal_sort_updated
}

private fun statusLabel(status: String) = when (status) {
    "watching" -> AYMR.strings.watching
    "reading" -> MR.strings.reading
    "completed" -> MR.strings.completed
    "on_hold" -> MR.strings.on_hold
    "dropped" -> MR.strings.dropped
    "plan_to_watch" -> AYMR.strings.plan_to_watch
    "plan_to_read" -> MR.strings.plan_to_read
    "rewatching", "rereading" -> MR.strings.repeating
    else -> MR.strings.completed
}

@Composable
internal fun MalMyListScreen(
    state: MalListScreenModel.State,
    events: kotlinx.coroutines.flow.Flow<MalListScreenModel.Event>,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onFilterChange: (MalListScreenModel.Filter) -> Unit,
    onMediaTypeChange: (MalListScreenModel.MediaType) -> Unit,
    onSearchOpenChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortByChange: (MalListScreenModel.Sort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onIncrement: (MalListScreenModel.MalListEntry) -> Unit,
    onSaveEntry: (MalListScreenModel.MalListEntry, status: String, score: Int, progress: Int) -> Unit,
    onRemoveEntry: (MalListScreenModel.MalListEntry) -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onOpenEntry: (MalListScreenModel.MalListEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        events.collectLatest { event ->
            val message = when (event) {
                MalListScreenModel.Event.Updated -> AYMR.strings.mal_list_entry_updated
                MalListScreenModel.Event.UpdateFailed -> AYMR.strings.mal_load_failed
                MalListScreenModel.Event.Removed -> AYMR.strings.mal_removed_from_list
            }
            snackbarHostState.showSnackbar(context.stringResource(message))
        }
    }
    if (!state.signedIn) {
        MalSignInPrompt(
            onSignIn = onSignIn,
            errorMessage = state.loginError,
            modifier = modifier,
        )
        return
    }

    val isAnime = state.mediaType == MalListScreenModel.MediaType.ANIME
    val unit = if (isAnime) "ep" else "ch"
    val filterLabels = if (isAnime) FilterLabels else FilterLabelsManga
    val filterShortLabels = if (isAnime) FilterLabelsShort else FilterLabelsShortManga

    var editingEntry by remember { mutableStateOf<MalListScreenModel.MalListEntry?>(null) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    val filteredEntries = remember(
        state.entries,
        state.query,
        state.sortBy,
        state.sortAscending,
        state.selectedFilter,
        state.mediaType,
    ) {
        val q = state.query.trim()
        val wantedStatus = state.selectedFilter.status(state.mediaType)
        val queried = state.entries
            .filter { wantedStatus == null || it.status == wantedStatus }
            .let { list -> if (q.isEmpty()) list else list.filter { it.title.contains(q, ignoreCase = true) } }
        // Stable alphabetical base so ties are deterministic
        val byTitle = queried.sortedBy { it.title.lowercase() }
        when (state.sortBy) {
            MalListScreenModel.Sort.STATUS ->
                if (state.sortAscending) byTitle.sortedBy { statusRank(it.status) } else byTitle.sortedByDescending { statusRank(it.status) }
            MalListScreenModel.Sort.ALPHABETICAL ->
                if (state.sortAscending) byTitle else byTitle.asReversed()
            MalListScreenModel.Sort.SCORE ->
                if (state.sortAscending) byTitle.sortedBy { it.score } else byTitle.sortedByDescending { it.score }
            MalListScreenModel.Sort.PROGRESS ->
                if (state.sortAscending) byTitle.sortedBy { it.progress } else byTitle.sortedByDescending { it.progress }
            MalListScreenModel.Sort.LAST_UPDATED ->
                if (state.sortAscending) {
                    byTitle.sortedBy { it.updatedAt ?: "" }
                } else {
                    byTitle.sortedByDescending { it.updatedAt ?: "" }
                }
        }.let { it.toImmutableList() }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.searchOpen) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = { onSearchOpenChange(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                placeholder = { Text(stringResource(AYMR.strings.mal_search_my_list)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                // Left slot: the selector owns its half now (search moved to the far right).
                // Falls back to short labels when the slot can't fit the full word.
                BoxWithConstraints(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                ) {
                    FilterDropdown(
                        selected = state.selectedFilter,
                        labels = filterLabels,
                        buttonLabels = if (maxWidth < 140.dp) filterShortLabels else filterLabels,
                        onSelect = onFilterChange,
                    )
                }
                AnimeMangaToggle(
                    selected = state.mediaType,
                    onSelect = onMediaTypeChange,
                )
                // Far-right controls: search + sort + direction; mirrors the left weight so the toggle stays centered
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(
                            onClick = { onSearchOpenChange(true) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(AYMR.strings.mal_search_my_list),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        SortDropdown(
                            selected = state.sortBy,
                            isAnime = isAnime,
                            onSelect = onSortByChange,
                        )
                        DirectionToggleButton(
                            ascending = state.sortAscending,
                            onToggle = onToggleSortDirection,
                        )
                    }
                }
            }
        }

        when {
            state.loading && state.entries.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.mal_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
            filteredEntries.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.mal_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        MalListRow(
                            entry = entry,
                            unit = unit,
                            updating = entry.id in state.updatingIds,
                            onIncrement = { onIncrement(entry) },
                            onEdit = { editingEntry = entry },
                            onClick = { onOpenEntry(entry) },
                        )
                    }
                }
            }
        }
    }
        if (showScrollToTop && filteredEntries.isNotEmpty()) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { scrollScope.launch { listState.scrollToItem(0, 0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(AYMR.strings.mal_scroll_top),
                )
            }
        }
    }

    editingEntry?.let { entry ->
        MalEditEntrySheet(
            entry = entry,
            isAnime = isAnime,
            unit = unit,
            onDismiss = { editingEntry = null },
            onSave = { status, score, progress ->
                onSaveEntry(entry, status, score, progress)
                editingEntry = null
            },
            onRemove = {
                onRemoveEntry(entry)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun FilterDropdown(
    selected: MalListScreenModel.Filter,
    labels: Map<MalListScreenModel.Filter, dev.icerock.moko.resources.StringResource>,
    onSelect: (MalListScreenModel.Filter) -> Unit,
    buttonLabels: Map<MalListScreenModel.Filter, dev.icerock.moko.resources.StringResource> = labels,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .clickable { expanded = true }
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(buttonLabels.getValue(selected)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            labels.forEach { (filter, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        onSelect(filter)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortDropdown(
    selected: MalListScreenModel.Sort,
    isAnime: Boolean,
    onSelect: (MalListScreenModel.Sort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(AYMR.strings.mal_sort_list),
                modifier = Modifier.size(20.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SortOptions.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(option, isAnime))) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun DirectionToggleButton(
    ascending: Boolean,
    onToggle: () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onToggle,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = stringResource(AYMR.strings.mal_sort_toggle_direction),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AnimeMangaToggle(
    selected: MalListScreenModel.MediaType,
    onSelect: (MalListScreenModel.MediaType) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp),
        ) {
            AnimeMangaToggleSegment(
                label = "Anime",
                selected = selected == MalListScreenModel.MediaType.ANIME,
                icon = ImageVector.vectorResource(R.drawable.ic_animelibrary_outline_24dp),
                onClick = { onSelect(MalListScreenModel.MediaType.ANIME) },
            )
            AnimeMangaToggleSegment(
                label = "Manga",
                selected = selected == MalListScreenModel.MediaType.MANGA,
                icon = ImageVector.vectorResource(R.drawable.ic_book_24dp),
                onClick = { onSelect(MalListScreenModel.MediaType.MANGA) },
            )
        }
    }
}

@Composable
private fun AnimeMangaToggleSegment(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun MalListRow(
    entry: MalListScreenModel.MalListEntry,
    unit: String,
    updating: Boolean,
    onIncrement: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plusEnabled = !updating &&
        entry.status != "completed" &&
        (entry.total == null || entry.progress < entry.total)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            data = entry.coverUrl,
            modifier = Modifier.width(68.dp),
            contentDescription = entry.title,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                entry.mediaType?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                entry.seasonLabel?.let { add(it) }
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val effectiveTotal = entry.total?.takeIf { it > 0 }
                ?: if (entry.status == "completed") entry.progress else null
            val barFraction = when {
                effectiveTotal != null && effectiveTotal > 0 ->
                    (entry.progress.toFloat() / effectiveTotal).coerceIn(0f, 1f)
                // Unknown total: trust the user — started shows half, never-started shows empty
                entry.status in listOf("watching", "reading", "rewatching", "rereading", "on_hold", "dropped") ->
                    0.5f
                else -> 0f // never started (plan to watch/read) — empty bar
            }
            val barColor = MalStatusSwatches[entry.status] ?: MaterialTheme.colorScheme.primary
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { barFraction },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Score chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (entry.score > 0) entry.score.toString() else "–",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = progressText(entry, unit),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionSquare(
                enabled = plusEnabled,
                onClick = onIncrement,
                containerColor = MalPlusAccent,
            ) {
                Text(
                    text = "+1",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (plusEnabled) {
                        MalPlusAccentOn
                    } else {
                        MalPlusAccentOn.copy(alpha = 0.35f)
                    },
                )
            }
            ActionSquare(
                enabled = true,
                onClick = onEdit,
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionSquare(
    enabled: Boolean,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (enabled) {
            containerColor
        } else {
            containerColor.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MalEditEntrySheet(
    entry: MalListScreenModel.MalListEntry,
    isAnime: Boolean,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (status: String, score: Int, progress: Int) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var status by remember { mutableStateOf(entry.status) }
    var score by remember { mutableStateOf(entry.score) }
    var progress by remember { mutableStateOf(entry.progress) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val statusOptions = if (isAnime) {
        listOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")
    } else {
        listOf("reading", "completed", "on_hold", "dropped", "plan_to_read")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            ItemCover.Book(
                data = entry.coverUrl,
                modifier = Modifier.width(84.dp),
                contentDescription = entry.title,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))

            SectionLabel(stringResource(AYMR.strings.mal_status))
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                statusOptions.forEach { value ->
                    val swatch = MalStatusSwatches[value] ?: MaterialTheme.colorScheme.surfaceVariant
                    val onSwatch = MalStatusOnSwatches[value] ?: MaterialTheme.colorScheme.onSurface
                    FilterChip(
                        selected = status == value,
                        onClick = { status = value },
                        label = {
                            Text(
                                text = stringResource(statusLabel(value)),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = swatch.copy(alpha = 0.15f),
                            labelColor = swatch,
                            selectedContainerColor = swatch,
                            selectedLabelColor = onSwatch,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionLabel(stringResource(AYMR.strings.mal_rating))
            StepperRow(
                value = score,
                min = 0,
                max = 10,
                onValueChange = { score = it },
            )
            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(if (isAnime) AYMR.strings.mal_episodes_watched else AYMR.strings.mal_chapters_read))
            StepperRow(
                value = progress,
                min = 0,
                max = (entry.total?.takeIf { it > 0 } ?: 9999),
                onValueChange = { progress = it },
                valueSuffix = progressSuffix(entry),
            )
            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(
                    onClick = { onSave(status, score, progress) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            }

            onRemove?.let {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                )
                OutlinedButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(AYMR.strings.mal_remove_from_list))
                }
            }
        }
    }

    if (showRemoveConfirm && onRemove != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = {
                Text(stringResource(AYMR.strings.mal_remove_confirm_title))
            },
            text = {
                Text(stringResource(AYMR.strings.mal_remove_confirm_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        onRemove()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(AYMR.strings.mal_remove_from_list))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun StepperButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StepperRow(
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    valueSuffix: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StepperButton(label = "−") { onValueChange((value - 1).coerceAtLeast(min)) }
        Text(
            text = buildString {
                append(value)
                valueSuffix?.let { append(it) }
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        StepperButton(label = "+") { onValueChange((value + 1).coerceAtMost(max)) }
    }
}
