package eu.kanade.tachiyomi.ui.mal

import android.app.Application
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Serializable
private data class CachedMalSearchHistory(
    val queries: List<String> = emptyList(),
)

/**
 * Stores the MAL/AniList search queries the user typed, so they can jump back to a
 * previous search with one tap. Mirrors [MalProfileStore] for the profile cache.
 */
object MalSearchHistoryStore {

    private const val MAX_ENTRIES = 20

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun dir(): File =
        File(Injekt.get<Application>().filesDir, "mal_list_cache").apply { mkdirs() }

    private fun file(): File = File(dir(), "search_history.json")

    suspend fun read(): List<String> = withIOContext {
        try {
            val f = file()
            if (!f.exists()) return@withIOContext emptyList()
            json.decodeFromString<CachedMalSearchHistory>(f.readText()).queries
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Adds the query to the history (most recent first, no duplicates).
     */
    suspend fun add(query: String) = withIOContext {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withIOContext
        val current = read()
        val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_ENTRIES)
        write(updated)
    }

    suspend fun remove(query: String) = withIOContext {
        val updated = read().filterNot { it.equals(query, ignoreCase = true) }
        write(updated)
    }

    suspend fun clear() = withIOContext {
        write(emptyList())
    }

    private suspend fun write(queries: List<String>) = withIOContext {
        try {
            file().writeText(
                json.encodeToString(CachedMalSearchHistory.serializer(), CachedMalSearchHistory(queries)),
            )
        } catch (e: Exception) {
            // non-fatal
        }
    }
}
