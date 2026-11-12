package eu.kanade.tachiyomi.ui.mal

import android.app.Application
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Serializable
private data class CachedMalProfile(
    val savedAt: Long,
    val profile: MALUserProfile,
)

/**
 * Tiny file-based cache for the user's MAL profile (avatar url, name, joined
 * date, birthday, gender, location) so the profile page opens instantly —
 * mirrors [MalListStore] for the list itself.
 */
object MalProfileStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun dir(): File =
        File(Injekt.get<Application>().filesDir, "mal_list_cache").apply { mkdirs() }

    private fun file(): File = File(dir(), "profile.json")

    suspend fun read(): MALUserProfile? =
        withIOContext {
            try {
                val f = file()
                if (!f.exists()) return@withIOContext null
                json.decodeFromString<CachedMalProfile>(f.readText()).profile
            } catch (e: Exception) {
                // Corrupted cache — treat as missing and let it refetch
                null
            }
        }

    suspend fun write(profile: MALUserProfile) = withIOContext {
        try {
            val cached = CachedMalProfile(
                savedAt = System.currentTimeMillis(),
                profile = profile,
            )
            file().writeText(json.encodeToString(CachedMalProfile.serializer(), cached))
        } catch (e: Exception) {
            // Cache write failures are non-fatal — the profile just won't open instantly next time
        }
    }

    suspend fun clear() = withIOContext {
        try {
            file().delete()
        } catch (e: Exception) {
            // non-fatal
        }
        Unit
    }
}
