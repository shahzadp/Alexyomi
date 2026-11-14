package eu.kanade.presentation.more.settings.screen.about

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * About - one page for what Alexyomi is and how to report problems.
 *
 * This used to be the last "Advanced" entry of the settings. Everything it held now lives here
 * (diagnostics, onboarding reset, notification settings) so the main page is the whole settings
 * tree, searchable from the one search button at the top.
 */
object AboutScreen : SearchableSettings {

    private const val SOURCE_CODE_URL = "https://github.com/shahzadp/Alexyomi"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_about

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_about),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.version),
                        subtitle = getVersionName(withBuildDate = true),
                        onClick = {
                            val deviceInfo = CrashLogUtil(context).getDebugInfo()
                            context.copyToClipboard("Debug information", deviceInfo)
                            context.toast(AYMR.strings.copied)
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.about_source_code),
                        subtitle = stringResource(AYMR.strings.about_source_code_summary),
                        onClick = { uriHandler.openUri(SOURCE_CODE_URL) },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_category_diagnostics),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_dump_crash_logs),
                        subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                        onClick = {
                            scope.launch {
                                CrashLogUtil(context).dumpLogs()
                            }
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = networkPreferences.verboseLogging(),
                        title = stringResource(MR.strings.pref_verbose_logging),
                        subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                        onValueChanged = {
                            context.toast(MR.strings.requires_app_restart)
                            true
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_debug_info),
                        onClick = { navigator.push(DebugInfoScreen()) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_onboarding_guide),
                        onClick = { navigator.push(OnboardingScreen()) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_manage_notifications),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                    ),
                ),
            ),
        )
    }

    fun getVersionName(withBuildDate: Boolean): String {
        return when {
            BuildConfig.DEBUG -> {
                "Debug ${BuildConfig.COMMIT_SHA}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
            isPreviewBuildType -> {
                "Preview r${BuildConfig.COMMIT_COUNT}".let {
                    if (withBuildDate) {
                        "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
            else -> {
                "Alexyomi ${BuildConfig.VERSION_NAME}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
        }
    }

    internal fun getFormattedBuildTime(): String {
        return try {
            LocalDateTime.ofInstant(
                Instant.parse(BuildConfig.BUILD_TIME),
                ZoneId.systemDefault(),
            )
                .toDateTimestampString(
                    UiPreferences.dateFormat(
                        Injekt.get<UiPreferences>().dateFormat().get(),
                    ),
                )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read build time" }
            BuildConfig.BUILD_TIME
        }
    }
}
