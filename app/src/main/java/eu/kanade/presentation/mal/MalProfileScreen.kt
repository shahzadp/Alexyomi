package eu.kanade.presentation.mal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.mal.MalListScreenModel
import eu.kanade.tachiyomi.ui.mal.MalProfileScreenModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Wc
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MalProfileScreen(
    state: MalProfileScreenModel.State,
    navigateUp: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenFriends: () -> Unit,
    onMediaTypeChange: (MalListScreenModel.MediaType) -> Unit,
    onRefresh: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(AYMR.strings.mal_profile),
                navigateUp = navigateUp,
            )
        },
        modifier = modifier,
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = state.reloading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
            if (!state.signedIn && !state.loading) {
                SignedOutContent(onOpenWeb)
                return@Column
            }

            ProfileHeader(state)

            Spacer(Modifier.height(12.dp))

            InfoCard(state)

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenWeb,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.mal_open_on_mal))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenFriends,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(AYMR.strings.mal_friends))
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimeMangaToggle(
                    selected = state.mediaType,
                    onSelect = onMediaTypeChange,
                )
            }

            Spacer(Modifier.height(20.dp))

            val stats = if (state.mediaType == MalListScreenModel.MediaType.MANGA) {
                state.mangaStats
            } else {
                state.animeStats
            }

            if (stats == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            StatsTiles(stats = stats, isAnime = state.mediaType == MalListScreenModel.MediaType.ANIME)

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(AYMR.strings.mal_status),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            StatusBreakdown(stats = stats, isAnime = state.mediaType == MalListScreenModel.MediaType.ANIME)

            Spacer(Modifier.height(24.dp))

            LogOutButton(
                onLogOut = onLogOut,
            )
            }
        }
    }
}

/**
 * Log out button that has to be pressed twice to confirm, so a stray tap doesn't sign the user out.
 */
@Composable
private fun LogOutButton(
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(confirming) {
        if (confirming) {
            delay(4.seconds)
            confirming = false
        }
    }

    OutlinedButton(
        onClick = {
            if (confirming) {
                confirming = false
                onLogOut()
            } else {
                confirming = true
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (confirming) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                if (confirming) AYMR.strings.mal_log_out_confirm else AYMR.strings.mal_log_out,
            ),
        )
    }
}

@Composable
private fun ProfileHeader(state: MalProfileScreenModel.State) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (state.avatarUrl != null) {
                AsyncImage(
                    model = state.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PersonOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.userName.takeIf { it.isNotBlank() } ?: "MyAnimeList",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_tracker_mal),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "MyAnimeList",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsTiles(
    stats: MalProfileScreenModel.MediaStats,
    isAnime: Boolean,
) {
    val countFormat = NumberFormat.getIntegerInstance()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            value = NumberFormat.getIntegerInstance().format(stats.progressSum),
            label = stringResource(
                if (isAnime) AYMR.strings.mal_episodes_watched else AYMR.strings.mal_chapters_read,
            ),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = stats.avgScore?.let { String.format(Locale.US, "%.2f", it) } ?: "–",
            label = stringResource(AYMR.strings.mal_avg_score),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = countFormat.format(stats.totalEntries),
            label = stringResource(AYMR.strings.mal_total_entries),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun StatusBreakdown(
    stats: MalProfileScreenModel.MediaStats,
    isAnime: Boolean,
) {
    val rows = listOf(
        Triple(malStatusSwatch("watching")!!, stringResource(if (isAnime) AYMR.strings.mal_watching else AYMR.strings.mal_reading), stats.watching),
        Triple(malStatusSwatch("completed")!!, stringResource(AYMR.strings.mal_completed), stats.completed),
        Triple(malStatusSwatch("plan_to_watch")!!, stringResource(if (isAnime) AYMR.strings.mal_plan_to_watch else AYMR.strings.mal_plan_to_read), stats.planned),
        Triple(malStatusSwatch("on_hold")!!, stringResource(AYMR.strings.mal_on_hold), stats.onHold),
        Triple(malStatusSwatch("dropped")!!, stringResource(AYMR.strings.mal_dropped), stats.dropped),
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { index, (swatch, label, count) ->
                StatusRow(swatch = swatch, label = label, count = count)
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    swatch: androidx.compose.ui.graphics.Color,
    label: String,
    count: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(swatch),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = NumberFormat.getIntegerInstance().format(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SignedOutContent(onOpenWeb: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PersonOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(AYMR.strings.mal_signed_out_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(AYMR.strings.mal_signed_out_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenWeb) {
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(AYMR.strings.mal_sign_in_action))
        }
    }
}

@Composable
private fun InfoCard(state: MalProfileScreenModel.State) {
    val items = buildList<Triple<ImageVector, StringResource, String>> {
        state.joinedAt?.let {
            add(Triple(Icons.Outlined.CalendarMonth, AYMR.strings.mal_joined_date, formatProfileDate(it)))
        }
        state.birthday?.let {
            add(Triple(Icons.Outlined.Cake, AYMR.strings.mal_birthday, formatProfileDate(it)))
        }
        state.gender?.let {
            add(Triple(Icons.Outlined.Wc, AYMR.strings.mal_gender, it.replaceFirstChar(Char::uppercase)))
        }
        state.location?.let {
            add(Triple(Icons.Outlined.Place, AYMR.strings.mal_location, it))
        }
    }
    if (items.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { index, (icon, labelRes, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(78.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

private fun formatProfileDate(raw: String): String {
    val instant = parseProfileInstant(raw) ?: return raw
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

private fun parseProfileInstant(raw: String): Instant? {
    runCatching { return OffsetDateTime.parse(raw.replace(' ', 'T')).toInstant() }
    runCatching { return LocalDate.parse(raw.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }
    return null
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
