package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.SourceMirrorRegistry
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Moves a request to a working mirror when the site answers with its website instead of the
 * requested api.
 *
 * Extensions can declare mirrors - alternative domains for the same site. When the primary
 * domain is decommissioned it serves the frontend for every path, so the extension gets a html
 * page where it expects json. This interceptor detects that and retries the request on the
 * mirrors, which usually serve the very same api.
 */
class SourceMirrorInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isDeadApiResponse(request, response)) return response

        val host = request.url.host
        val mirrors = SourceMirrorRegistry.mirrorsFor(host)
        if (mirrors.isEmpty()) return response

        for (mirror in mirrors) {
            if (mirror == host) continue

            val mirrorRequest = request.newBuilder()
                .url(request.url.newBuilder().host(mirror).build())
                .build()

            val mirrorResponse = try {
                chain.proceed(mirrorRequest)
            } catch (e: IOException) {
                continue
            }

            // The mirror answers with the api, use it
            if (!isDeadApiResponse(mirrorRequest, mirrorResponse)) {
                response.close()
                return mirrorResponse
            }

            mirrorResponse.close()
        }

        return response
    }

    /**
     * Whether the response is a website where the caller asked for json.
     *
     * Only then it is safe to assume that the domain is broken and not that the server
     * legitimately answers with a page.
     */
    private fun isDeadApiResponse(request: Request, response: Response): Boolean {
        if (!response.isSuccessful) return false

        val contentType = response.header("Content-Type")?.lowercase() ?: return false
        if (!contentType.contains("html")) return false

        val accept = request.header("Accept")?.lowercase() ?: return false
        return accept.substringBefore(',').trim().contains("json")
    }
}
