package eu.kanade.tachiyomi.util

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.forEach
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.SourceMirrorRegistry
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.sourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Finds the alternative hosts (mirrors) of the installed extensions.
 *
 * Extensions that ship mirrors list them in their own settings, so they can be read from
 * there. The app uses them to move a request to a working mirror when the primary domain
 * stopped serving the api.
 */
object SourceMirrorSetup {

    /**
     * The key of the list preference the extensions use to pick a mirror.
     */
    private const val MIRROR_PREFERENCE_KEY = "preferred_mirror"

    /**
     * Registers the mirrors of every installed source.
     */
    fun registerMirrors(context: Context) {
        animeSources().forEach { source -> collect(context, source) }
        mangaSources().forEach { source -> collect(context, source) }
    }

    /**
     * Resolves the mirrors of a host on demand.
     */
    fun installResolver(context: Context) {
        SourceMirrorRegistry.setResolver { host ->
            animeSources().forEach { source -> collect(context, source, host) }
            mangaSources().forEach { source -> collect(context, source, host) }
            SourceMirrorRegistry.mirrorsFor(host)
        }
    }

    private fun animeSources(): List<ConfigurableAnimeSource> {
        val extensions = try {
            Injekt.get<AnimeExtensionManager>().installedExtensionsFlow.value
        } catch (e: Exception) {
            emptyList()
        }
        return extensions.flatMap { it.sources }.filterIsInstance<ConfigurableAnimeSource>()
    }

    private fun mangaSources(): List<ConfigurableSource> {
        val extensions = try {
            Injekt.get<MangaExtensionManager>().installedExtensionsFlow.value
        } catch (e: Exception) {
            emptyList()
        }
        return extensions.flatMap { it.sources }.filterIsInstance<ConfigurableSource>()
    }

    /**
     * Reads the mirror hosts of the source and registers them.
     *
     * @param onlyForHost when set, the mirrors are only registered if they belong to that host
     */
    private fun collect(context: Context, source: Any, onlyForHost: String? = null) {
        val mirrors = mirrorHosts(context, source) ?: return
        if (mirrors.isEmpty()) return

        val hosts = mutableListOf<String>()
        sourceBaseUrlHost(source)?.let { hosts += it }
        hosts += mirrors

        if (onlyForHost != null && onlyForHost !in hosts) return

        SourceMirrorRegistry.register(hosts, mirrors)
    }

    /**
     * The mirror hosts of the source, taken from its mirror preference.
     */
    private fun mirrorHosts(context: Context, source: Any): List<String>? {
        val screen = buildScreen(context, source) ?: return null
        var result: List<String>? = null
        screen.forEach { preference ->
            if (preference is ListPreference && preference.key == MIRROR_PREFERENCE_KEY) {
                result = preference.entries?.map { it.toString() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            }
        }
        return result
    }

    private fun sourceBaseUrlHost(source: Any): String? = when (source) {
        is AnimeHttpSource -> hostOf(source.baseUrl)
        is HttpSource -> hostOf(source.baseUrl)
        else -> null
    }

    private fun hostOf(url: String): String? = try {
        android.net.Uri.parse(url).host
    } catch (e: Exception) {
        null
    }

    /**
     * Builds the preference screen of the source, the same way the app shows it.
     */
    private fun buildScreen(context: Context, source: Any): PreferenceScreen? = try {
        val themed = ContextThemeWrapper(context, R.attr.preferenceTheme)
        val manager = PreferenceManager(themed)
        val screen = manager.createPreferenceScreen(themed)
        when (source) {
            is ConfigurableAnimeSource -> {
                manager.preferenceDataStore = SharedPreferencesDataStore(source.sourcePreferences())
                source.setupPreferenceScreen(screen)
            }
            is ConfigurableSource -> {
                manager.preferenceDataStore = SharedPreferencesDataStore(source.sourcePreferences())
                source.setupPreferenceScreen(screen)
            }
            else -> return null
        }
        screen
    } catch (e: Exception) {
        null
    }
}
