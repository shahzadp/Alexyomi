package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class EnableAutoBackupMigration : Migration {
    override val version: Float = 84f

    // Backup/restore removed; no backup worker is scheduled.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean = true
}
