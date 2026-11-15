package eu.kanade.presentation.movies

import android.content.Context
import logcat.LogPriority
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.system.logcat
import java.util.UUID

@Serializable
data class SavedFolder(
    val id: String,
    val name: String,
    val color: Int,
)

@Serializable
data class SavedLink(
    val name: String,
    val url: String,
    val folderId: String? = null,
)

@Serializable
data class SavedLinksData(
    val folders: List<SavedFolder> = emptyList(),
    val links: List<SavedLink> = emptyList(),
)

/**
 * Persists the Movies tab's saved links and their folders as JSON in SharedPreferences.
 */
object SavedLinksStore {
    private const val PREFS = "movies_saved_links"
    private const val KEY = "links"

    private val json = Json { ignoreUnknownKeys = true }

    // Pleasant, sufficiently distinct folder colors; new folders always get an unused one.
    private val FOLDER_PALETTE = listOf(
        0xFFE53935.toInt(), // red
        0xFFD81B60.toInt(), // pink
        0xFF8E24AA.toInt(), // purple
        0xFF5E35B1.toInt(), // deep purple
        0xFF3949AB.toInt(), // indigo
        0xFF1E88E5.toInt(), // blue
        0xFF039BE5.toInt(), // light blue
        0xFF00897B.toInt(), // teal
        0xFF43A047.toInt(), // green
        0xFF7CB342.toInt(), // light green
        0xFFFDD835.toInt(), // yellow
        0xFFFFB300.toInt(), // amber
        0xFFFB8C00.toInt(), // orange
        0xFFF4511E.toInt(), // deep orange
        0xFF6D4C41.toInt(), // brown
        0xFF546E7A.toInt(), // blue grey
    )

    /** Picks the first unused palette color; if every color is used, falls back to a dark random one. */
    fun nextFolderColor(used: List<Int>): Int =
        FOLDER_PALETTE.firstOrNull { it !in used }
            ?: ((FOLDER_PALETTE.random() and 0x00FFFFFF) or 0xFF000000.toInt())

    fun newFolderId(): String = UUID.randomUUID().toString()

    fun load(context: Context): SavedLinksData = runCatching {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return@runCatching SavedLinksData()
        // Current format: {"folders": [...], "links": [...]}
        runCatching {
            json.decodeFromString<SavedLinksData>(raw)
        }.getOrElse {
            // Migration from the flat list-of-links format.
            val legacy = json.decodeFromString<List<SavedLink>>(raw)
            SavedLinksData(links = legacy)
        }
    }.getOrElse {
        logcat(LogPriority.ERROR, it) { "Failed to load saved links" }
        SavedLinksData()
    }

    fun save(context: Context, data: SavedLinksData) {
        runCatching {
            val raw = json.encodeToString(data)
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, raw)
                .apply()
        }.onFailure {
            logcat(LogPriority.ERROR, it) { "Failed to save links" }
        }
    }
}
