package eu.kanade.presentation.mal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseItem

@Composable
fun MalAnimeGrid(
    items: List<AnilistBrowseItem>,
    isLoading: Boolean,
    onClickItem: (AnilistBrowseItem) -> Unit,
    modifier: Modifier = Modifier,
    hasNextPage: Boolean = false,
    isLoadingMore: Boolean = false,
    onShowMore: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.anilistId }) { anime ->
                Column {
                    ItemCover.Book(
                        data = anime.coverUrl,
                        modifier = Modifier.fillMaxWidth(),
                        contentDescription = anime.title,
                        onClick = { onClickItem(anime) },
                    )
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(onClick = { onClickItem(anime) })
                            .padding(vertical = 2.dp),
                    )
                    anime.scoreText?.let {
                        Text(
                            text = "★ $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hasNextPage) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoadingMore && onShowMore != null) { onShowMore?.invoke() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        } else {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onShowMore?.invoke() },
                                enabled = !isLoadingMore,
                            ) {
                                Text("Show more")
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
        }
    }
}
