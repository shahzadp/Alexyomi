package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

class RemoveOneTwoHourUpdateMigration : Migration {
    override val version = 61f

    // Handle removed every 1 or 2 hour library updates.
    // The library update jobs are gone; only normalize the stored preference.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false

        val updateInterval = libraryPreferences.autoUpdateInterval().get()
        if (updateInterval == 1 || updateInterval == 2) {
            libraryPreferences.autoUpdateInterval().set(3)
        }

        return true
    }
}
