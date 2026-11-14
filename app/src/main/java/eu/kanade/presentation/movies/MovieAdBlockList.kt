package eu.kanade.presentation.movies

import android.content.Context
import android.net.Uri
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Vivaldi-style network blocker for the Movies tab.
 *
 * The tab used to check a small hand-rolled list of 25 ad hosts and "block" ads
 * simply by not helping them. That is why every ad slipped through. This loader
 * reads a real filter set instead: the pure-domain rules of EasyList and
 * EasyPrivacy (||host^ and ||host^$third-party), extracted at build time and
 * stored in assets as movies/blocklist.txt.
 *
 * Matching is suffix-based: `a.b.ads.example.com` hits `ads.example.com`.
 * Third-party rules only apply when the initiator of the request (Referer, or
 * the page currently being navigated) is a different site.
 */
internal object MovieAdBlockList {

    private val alwaysBlock = HashSet<String>()
    private val thirdPartyOnly = HashSet<String>()
    @Volatile
    private var loaded = false

    /** Loads the blocklist synchronously. Safe to call more than once. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                context.assets.open("movies/blocklist.txt").use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine { raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                        if (line.startsWith("t!")) {
                            thirdPartyOnly.add(line.substring(2))
                        } else {
                            alwaysBlock.add(line)
                        }
                    }
                }
                loaded = true
                logcat {
                    "MovieAdBlockList loaded: ${alwaysBlock.size} always, ${thirdPartyOnly.size} third-party"
                }
            } catch (e: Exception) {
                // Fail open: blocking nothing still beats crashing the tab.
                logcat(LogPriority.ERROR, e) { "MovieAdBlockList failed to load" }
            }
        }
    }

    fun isBlocked(host: String?, initiatorHost: String?): Boolean {
        if (host.isNullOrBlank()) return false
        if (!loaded) return false
        val h = host.lowercase().trimEnd('.')
        var cur = h
        while (true) {
            if (alwaysBlock.contains(cur)) return true
            if (thirdPartyOnly.contains(cur) && isThirdParty(h, initiatorHost)) return true
            val dot = cur.indexOf('.')
            if (dot < 0) break
            cur = cur.substring(dot + 1)
        }
        return false
    }

    private fun isThirdParty(host: String, initiatorHost: String?): Boolean {
        if (initiatorHost.isNullOrBlank()) return false
        val a = host.lowercase().trimEnd('.')
        val b = initiatorHost.lowercase().trimEnd('.')
        if (a == b) return false
        if (a.endsWith(".$b") || b.endsWith(".$a")) return false
        return true
    }
}

/** Host of the Referer/Origin header of the request, if present. Used for third-party detection. */
internal fun refererHost(request: android.webkit.WebResourceRequest?): String? {
    val headers = request?.requestHeaders ?: return null
    val referer = headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
        ?: headers.entries.firstOrNull { it.key.equals("origin", ignoreCase = true) }?.value
        ?: return null
    return runCatching { Uri.parse(referer).host }.getOrNull()
}
