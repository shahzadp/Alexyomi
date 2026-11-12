package mihon.core.migration.migrations

import android.app.Application
import androidx.work.WorkManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class UseWorkManagerMigration : Migration {
    override val version = 96f

    // The library update jobs were removed along with the library feature.
    // Cancel any WorkManager tasks a previous version may have scheduled,
    // otherwise stale periodic work would keep crashing in the background.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val workManager = WorkManager.getInstance(context)
        listOf(
            "LibraryUpdate-auto",
            "LibraryUpdate-manual",
            "AnimeLibraryUpdate-auto",
            "AnimeLibraryUpdate-manual",
        ).forEach(workManager::cancelUniqueWork)

        return true
    }
}
