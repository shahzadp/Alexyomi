package eu.kanade.tachiyomi.util

import android.net.Uri
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.network.SourceMirrorRegistry
import eu.kanade.tachiyomi.ui.mal.MalProgressSync

/**
 * Registers the alternative hosts (mirrors) of the installed extensions.
 *
 * Extensions can ship mirrors - alternative domains that serve the same site. When the primary
 * domain stops serving the api and answers with the website instead, the extension breaks,
 * because it has no way of knowing about the mirrors on its own.
 *
 * The mirrors are part of the extension repository index, so they are read from there.
 */
object SourceMirrorSetup {

    /**
     * Registers the mirrors of the given sources. The url is the primary one, the list are the
     * alternatives.
     */
    fun registerMirrors(sources: List<Pair<String, List<String>>>) {
        sources.forEach { (baseUrl, mirrorUrls) -> register(baseUrl, mirrorUrls) }
    }

    private fun register(baseUrl: String, mirrorUrls: List<String>) {
        val mirrors = mirrorUrls.mapNotNull { hostOf(it) }.distinct()
        if (mirrors.isEmpty()) return

        val hosts = mutableListOf<String>()
        hostOf(baseUrl)?.let { hosts += it }
        hosts += mirrors

        SourceMirrorRegistry.register(hosts, mirrors)
    }

    /**
     * Reads the mirrors of the installed extensions from the repository index.
     *
     * This is only a fallback - the index is also read when the extension list is opened, and
     * that registers the mirrors as well.
     */
    suspend fun refreshMirrors() {
        val sources = mutableListOf<Pair<String, List<String>>>()

        runCatching {
            AnimeExtensionApi().findExtensions()
        }.getOrNull()?.forEach { extension ->
            extension.sources.forEach { source ->
                if (source.mirrorUrls.isNotEmpty()) sources += source.baseUrl to source.mirrorUrls
            }
        }

        runCatching {
            MangaExtensionApi().findExtensions()
        }.getOrNull()?.forEach { extension ->
            extension.sources.forEach { source ->
                if (source.mirrorUrls.isNotEmpty()) sources += source.baseUrl to source.mirrorUrls
            }
        }

        registerMirrors(sources)

        MalProgressSync.logEvent("Registered mirrors for ${SourceMirrorRegistry.registeredCount()} host(s)")
    }

    private fun hostOf(url: String): String? = try {
        Uri.parse(url).host
    } catch (e: Exception) {
        null
    }
}
