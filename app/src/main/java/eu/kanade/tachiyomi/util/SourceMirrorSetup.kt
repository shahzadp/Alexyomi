package eu.kanade.tachiyomi.util

import android.net.Uri
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.network.SourceMirrorRegistry
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.mal.MalProgressSync
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    /**
     * Reads the mirrors of the installed extensions from the extensions themselves.
     *
     * An extension that ships mirrors keeps them in a `MirrorPreferences` object, which holds
     * the list of the alternative domains. It is not part of the extension's settings screen,
     * so it is read from the object.
     */
    fun registerMirrorsFromExtensions() {
        val sources = mutableListOf<Pair<String, List<String>>>()

        animeSources().forEach { source ->
            val mirrors = mirrorUrlsOf(source)
            if (mirrors.isEmpty()) return@forEach
            hostOf(source.baseUrl)?.let { sources += it to mirrors }
        }

        mangaSources().forEach { source ->
            val mirrors = mirrorUrlsOf(source)
            if (mirrors.isEmpty()) return@forEach
            hostOf(source.baseUrl)?.let { sources += it to mirrors }
        }

        sources.forEach { (host, mirrors) ->
            val hosts = mirrors.mapNotNull { hostOf(it) }.distinct()
            if (hosts.isEmpty()) return@forEach
            SourceMirrorRegistry.register(listOf(host) + hosts, hosts)
        }

        MalProgressSync.logEvent(
            "Registered mirrors for ${SourceMirrorRegistry.registeredCount()} host(s) from the extensions",
        )
    }

    /**
     * The alternative domains of the extension.
     *
     * An extension that ships mirrors keeps them in an object that holds a list of
     * label/url pairs. The object is found by its structure, so it does not matter that the
     * extension is obfuscated.
     */
    private fun mirrorUrlsOf(source: Any): List<String> {
        return try {
            for (field in source.javaClass.declaredFields) {
                if (field.type.isPrimitive || field.type.isEnum) continue
                val value = runCatching {
                    field.isAccessible = true
                    field.get(source)
                }.getOrNull() ?: continue
                if (value == null || !field.type.isInstance(value)) continue

                for (inner in value.javaClass.declaredFields) {
                    if (inner.type != Array::class.java) continue
                    val array = runCatching {
                        inner.isAccessible = true
                        inner.get(value)
                    }.getOrNull() as? Array<*> ?: continue

                    val urls = array.mapNotNull { (it as? Pair<*, *>)?.second as? String }
                    if (urls.isNotEmpty()) return urls
                }
            }
            emptyList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun animeSources(): List<AnimeHttpSource> {
        val extensions = try {
            Injekt.get<AnimeExtensionManager>().installedExtensionsFlow.value
        } catch (e: Exception) {
            emptyList()
        }
        return extensions.flatMap { it.sources }.filterIsInstance<AnimeHttpSource>()
    }

    private fun mangaSources(): List<HttpSource> {
        val extensions = try {
            Injekt.get<MangaExtensionManager>().installedExtensionsFlow.value
        } catch (e: Exception) {
            emptyList()
        }
        return extensions.flatMap { it.sources }.filterIsInstance<HttpSource>()
    }

    private fun hostOf(url: String): String? = try {
        Uri.parse(url).host
    } catch (e: Exception) {
        null
    }
}
