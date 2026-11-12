package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.ui.model.StartScreen
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NavigationOptionsMigration : Migration {
    override val version = 120f

    // Navigation is fixed now: MAL first, no library tabs. Clear the legacy
    // per-user navigation preferences so they can't resurrect hidden tabs.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        prefs.edit {
            remove("bottom_nav_style")
            remove("default_home_tab_library")
            remove("bottom_rail_nav_style")
        }
        preferenceStore.getEnum("start_screen", StartScreen.MAL).set(StartScreen.MAL)

        return true
    }
}
