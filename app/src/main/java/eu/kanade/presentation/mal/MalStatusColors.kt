package eu.kanade.presentation.mal

import androidx.compose.ui.graphics.Color

/** Single source for the MAL list-status swatches used by FAB and chips. */
internal val MalStatusSwatches: Map<String, Color> = mapOf(
    "watching" to Color(0xFF22C55E),
    "reading" to Color(0xFF22C55E),
    "rewatching" to Color(0xFF22C55E),
    "rereading" to Color(0xFF22C55E),
    "completed" to Color(0xFF3B82F6),
    "plan_to_watch" to Color(0xFF9E9E9E),
    "plan_to_read" to Color(0xFF9E9E9E),
    "on_hold" to Color(0xFFEAB308),
    "dropped" to Color(0xFFEF4444),
)

internal val MalStatusOnSwatches: Map<String, Color> = mapOf(
    "watching" to Color(0xFF062B14),
    "reading" to Color(0xFF062B14),
    "rewatching" to Color(0xFF062B14),
    "rereading" to Color(0xFF062B14),
    "completed" to Color.White,
    "plan_to_watch" to Color(0xFF1F1F1F),
    "plan_to_read" to Color(0xFF1F1F1F),
    "on_hold" to Color(0xFF241E00),
    "dropped" to Color.White,
)

/**
 * The colour of a list status, or null when the status is unknown.
 */
internal fun malStatusSwatch(status: String?): Color? =
    status?.let { MalStatusSwatches[it.lowercase()] }

/**
 * The colour that is readable on top of the status colour.
 *
 * Falls back to white, so an unknown status can never crash the screen.
 */
internal fun malStatusOnSwatch(status: String?): Color =
    status?.let { MalStatusOnSwatches[it.lowercase()] } ?: Color.White
