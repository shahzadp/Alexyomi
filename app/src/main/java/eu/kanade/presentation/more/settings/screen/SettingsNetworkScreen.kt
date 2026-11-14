package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_LIBREDNS
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import okhttp3.Headers
import tachiyomi.core.common.i18n.stringResource
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Network settings — connectivity, DNS, user agent, the background battery optimizations and the
 * data saver. These were hidden inside Advanced before.
 */
object SettingsNetworkScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_network

    @Composable
    override fun getPreferences(): List<Preference> {
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }

        return listOf(
            getBackgroundActivityGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getDataSaverGroup(),
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_disable_battery_optimization),
                    subtitle = stringResource(MR.strings.pref_disable_battery_optimization_summary),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                context.toast(MR.strings.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            context.toast(MR.strings.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                        PREF_DOH_LIBREDNS to "LibreDNS",
                    ),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(MR.strings.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDataSaverGroup(): Preference.PreferenceGroup {
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val dataSaver by sourcePreferences.dataSaver().collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.data_saver),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = sourcePreferences.dataSaver(),
                    entries = persistentMapOf(
                        DataSaver.NONE to stringResource(MR.strings.disabled),
                        DataSaver.BANDWIDTH_HERO to stringResource(AYMR.strings.bandwidth_hero),
                        DataSaver.WSRV_NL to stringResource(AYMR.strings.wsrv),
                        DataSaver.RESMUSH_IT to stringResource(AYMR.strings.resmush),
                    ),
                    title = stringResource(AYMR.strings.data_saver),
                    subtitle = stringResource(AYMR.strings.data_saver_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = sourcePreferences.dataSaverServer(),
                    title = stringResource(AYMR.strings.bandwidth_data_saver_server),
                    subtitle = stringResource(AYMR.strings.data_saver_server_summary),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverDownloader(),
                    title = stringResource(AYMR.strings.data_saver_downloader),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverIgnoreJpeg(),
                    title = stringResource(AYMR.strings.data_saver_ignore_jpeg),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverIgnoreGif(),
                    title = stringResource(AYMR.strings.data_saver_ignore_gif),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = sourcePreferences.dataSaverImageQuality(),
                    entries = listOf(
                        "10%",
                        "20%",
                        "40%",
                        "50%",
                        "70%",
                        "80%",
                        "90%",
                        "95%",
                    ).associateBy { it.trimEnd('%').toInt() }.toPersistentMap(),
                    title = stringResource(AYMR.strings.data_saver_image_quality),
                    subtitle = stringResource(AYMR.strings.data_saver_image_quality_summary),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                kotlin.run {
                    val dataSaverImageFormatJpeg by sourcePreferences.dataSaverImageFormatJpeg().collectAsState()
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.dataSaverImageFormatJpeg(),
                        title = stringResource(AYMR.strings.data_saver_image_format),
                        subtitle = if (dataSaverImageFormatJpeg) {
                            stringResource(AYMR.strings.data_saver_image_format_summary_on)
                        } else {
                            stringResource(AYMR.strings.data_saver_image_format_summary_off)
                        },
                        enabled = dataSaver != DataSaver.NONE && dataSaver != DataSaver.RESMUSH_IT,
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverColorBW(),
                    title = stringResource(AYMR.strings.data_saver_color_bw),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
            ),
        )
    }
}
