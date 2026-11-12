package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.os.Environment
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The default storage folder is derived from the app name. The app used to be called "Aniyomi",
 * so installs that never picked a custom folder have their downloads in the old directory.
 * This moves that folder over so downloads keep working after the rename.
 */
object LegacyStorageMigration {

    private const val LEGACY_BASE_DIR_NAME = "Aniyomi"

    fun migrateIfNeeded(context: Context) {
        try {
            val storagePreferences = Injekt.get<StoragePreferences>()
            // A custom location was picked, nothing to move
            if (storagePreferences.baseStorageDirectory().isSet()) return

            val root = Environment.getExternalStorageDirectory() ?: return
            val legacy = File(root, LEGACY_BASE_DIR_NAME)
            if (!legacy.exists()) return

            val current = File(root, context.stringResource(MR.strings.app_name))
            if (current.exists()) return

            if (legacy.renameTo(current)) {
                logcat(LogPriority.INFO) { "Moved legacy storage folder to ${current.name}" }
                // The download caches still point at the old path, rebuild them
                Injekt.get<MangaDownloadCache>().invalidateCache()
                Injekt.get<AnimeDownloadCache>().invalidateCache()
            } else {
                logcat(LogPriority.ERROR) { "Failed to move legacy storage folder" }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to migrate the legacy storage folder" }
        }
    }
}
