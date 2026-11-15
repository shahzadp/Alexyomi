package eu.kanade.presentation.movies

import android.content.Context
import logcat.LogPriority
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.system.logcat

@Serializable
data class SavedLink(
    val name: String,
    val url: String,
)

/**
 * Persists the Movies tab's saved links in SharedPreferences as JSON.
 */
object SavedLinksStore {
    private const val PREFS = "movies_saved_links"
    private const val KEY = "links"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<SavedLink> = runCatching {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return@runCatching emptyList()
        json.decodeFromString<List<SavedLink>>(raw)
    }.getOrElse {
        logcat(LogPriority.ERROR, it) { "Failed to load saved links" }
        emptyList()
    }

    fun save(context: Context, links: List<SavedLink>) {
        runCatching {
            val raw = json.encodeToString(links)
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
