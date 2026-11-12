package eu.kanade.presentation.mal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseItem
import eu.kanade.tachiyomi.ui.mal.MalBrowseScreenModel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import java.time.LocalDate

private val Seasons = listOf(
    "WINTER" to "Winter",
    "SPRING" to "Spring",
    "SUMMER" to "Summer",
    "FALL" to "Fall",
)

private fun seasonTitle(state: MalBrowseScreenModel.State): String =
    state.season.lowercase().replaceFirstChar { it.uppercase() } + " " + state.seasonYear

@Composable
fun MalSeasonalScreen(
    state: MalBrowseScreenModel.State,
    onRetry: () -> Unit,
    onItemClick: (AnilistBrowseItem) -> Unit,
    onSeasonChange: (String, Int) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentYear = LocalDate.now().year

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = seasonTitle(state),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSeasonChange(state.season, state.seasonYear - 1) },
                    enabled = state.seasonYear > 1970,
                ) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
                }
                Text(
                    text = state.seasonYear.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { onSeasonChange(state.season, state.seasonYear + 1) },
                    enabled = state.seasonYear < currentYear + 1,
                ) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next year")
                }
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Seasons) { (key, label) ->
                FilterChip(
                    selected = state.season == key,
                    onClick = { onSeasonChange(key, state.seasonYear) },
                    label = { Text(label) },
                )
            }
        }
        BrowseContent(
            state = state,
            onRetry = onRetry,
            onItemClick = onItemClick,
            onShowMore = onShowMore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MalTopScreen(
    state: MalBrowseScreenModel.State,
    onSelectFilter: (MalBrowseScreenModel.Source) -> Unit,
    onRetry: () -> Unit,
    onShowMore: () -> Unit,
    onItemClick: (AnilistBrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        MalBrowseScreenModel.Source.TOP_SCORE to "Top score",
        MalBrowseScreenModel.Source.TOP_POPULAR to "Popular",
        MalBrowseScreenModel.Source.TOP_AIRING to "Airing",
    )

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters.size) { index ->
                val (source, label) = filters[index]
                FilterChip(
                    selected = state.source == source,
                    onClick = { onSelectFilter(source) },
                    label = { Text(label) },
                )
            }
        }
        BrowseContent(
            state = state,
            onRetry = onRetry,
            onItemClick = onItemClick,
            onShowMore = onShowMore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BrowseContent(
    state: MalBrowseScreenModel.State,
    onRetry: () -> Unit,
    onItemClick: (AnilistBrowseItem) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading && state.items.isEmpty() -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.error && state.items.isEmpty() -> {
            Column(
                modifier = modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(AYMR.strings.mal_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
        }
        else -> MalAnimeGrid(
            items = state.items,
            isLoading = state.loading,
            onClickItem = onItemClick,
            modifier = modifier,
            hasNextPage = state.hasNextPage,
            isLoadingMore = state.loadingMore,
            onShowMore = onShowMore,
        )
    }
}
