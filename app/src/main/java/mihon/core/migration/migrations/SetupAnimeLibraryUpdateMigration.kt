package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class SetupAnimeLibraryUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    // The library feature was removed; scheduled library updates are gone.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean = true
}
