package eu.kanade.tachiyomi.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Knows the alternative hosts (mirrors) of the installed extensions.
 *
 * Extensions can ship mirrors - alternative domains that serve the same site. When the primary
 * domain stops serving the api and answers with the website instead, the extension breaks,
 * because it has no way of knowing about the mirrors on its own.
 *
 * The hosts are registered by the app, the interceptor uses them to retry a request on a
 * working mirror.
 */
object SourceMirrorRegistry {

    private val mirrors = ConcurrentHashMap<String, List<String>>()

    @Volatile
    private var resolver: ((String) -> List<String>)? = null

    /**
     * Registers the mirror hosts of a source. The primary host is registered as well, so a
     * request to any of the mirrors can be moved to a working one.
     */
    fun register(hosts: Collection<String>, mirrorHosts: List<String>) {
        val mirrors = mirrorHosts.filter { it.isNotBlank() }.distinct()
        if (mirrors.isEmpty()) return
        hosts.filter { it.isNotBlank() }.forEach { this.mirrors[it] = mirrors }
    }

    /**
     * Sets the callback that resolves the mirrors of a host on demand.
     */
    fun setResolver(resolver: (String) -> List<String>) {
        this.resolver = resolver
    }

    /**
     * The mirror hosts of the given host, empty when there are none.
     */
    fun mirrorsFor(host: String): List<String> {
        mirrors[host]?.let { return it }
        val resolver = resolver ?: return emptyList()
        val resolved = try {
            resolver(host)
        } catch (e: Exception) {
            emptyList()
        }
        if (resolved.isNotEmpty()) {
            mirrors[host] = resolved
        }
        return resolved
    }
}
