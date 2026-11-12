package eu.kanade.tachiyomi.ui.mal

import android.app.Application
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Serializable
private data class CachedMalList(
    val savedAt: Long,
    val entries: List<MalListScreenModel.MalListEntry>,
)

/**
 * Tiny file-based cache for the user's MAL list so My List opens instantly
 * (without re-downloading every page of the list on every app launch).
 */
object MalListStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun dir(): File =
        File(Injekt.get<Application>().filesDir, "mal_list_cache").apply { mkdirs() }

    private fun file(mediaType: MalListScreenModel.MediaType): File =
        File(dir(), if (mediaType == MalListScreenModel.MediaType.ANIME) "anime_list.json" else "manga_list.json")

    suspend fun read(mediaType: MalListScreenModel.MediaType): List<MalListScreenModel.MalListEntry>? =
        withIOContext {
            try {
                val f = file(mediaType)
                if (!f.exists()) return@withIOContext null
                json.decodeFromString<CachedMalList>(f.readText()).entries
            } catch (e: Exception) {
                // Corrupted cache — treat as missing and let it be refetched
                null
            }
        }

    suspend fun write(mediaType: MalListScreenModel.MediaType, entries: List<MalListScreenModel.MalListEntry>) =
        withIOContext {
            try {
                val f = file(mediaType)
                val cached = CachedMalList(savedAt = System.currentTimeMillis(), entries = entries)
                f.writeText(json.encodeToString(CachedMalList.serializer(), cached))
                MalRevision.markStatsDirty()
            } catch (e: Exception) {
                // Cache write failures are non-fatal — the list just won't open instantly next time
            }
        }

    suspend fun clear(mediaType: MalListScreenModel.MediaType) = withIOContext {
        try {
            file(mediaType).delete()
        } catch (e: Exception) {
            // non-fatal
        }
        Unit
    }
}
