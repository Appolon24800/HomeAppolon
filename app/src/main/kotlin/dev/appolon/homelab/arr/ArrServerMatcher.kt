package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ServerEndpoint
import java.net.URI

/**
 * Decides whether a dashboard (Homer) service URL points at one of the
 * configured [ArrServer]s, so tapping the service can open the native arr
 * shell instead of the WebView. Pure JVM string logic on purpose: no Android
 * types, so the rules are unit tested without Robolectric.
 *
 * Rules, evaluated per server in registry order (first match wins):
 * 1. Strict: scheme, host, and effective port are equal, and the server's
 *    base path is empty, equal to, or a whole-segment prefix of the service
 *    path (`/radarr` matches `/radarr` and `/radarr/movie/12` but not
 *    `/radarr2`).
 * 2. Loose: the hosts are equal, the ports do not explicitly differ, and the
 *    server's non-empty base path appears inside the service URL string.
 *    This covers proxy aliases such as `http://nas/apps/sonarr` for a server
 *    stored as `http://nas:8989/sonarr`. Two explicitly different ports never
 *    match, which keeps a `host:7878` service from opening the `host:8989`
 *    server next to it.
 */
object ArrServerMatcher {

    /** The configured server the dashboard entry maps to, or null when it should stay a WebView page. */
    fun match(serviceUrl: String, servers: List<ArrServer>): ServerId? {
        val trimmed = serviceUrl.trim()
        val service = parse(trimmed) ?: return null
        for (server in servers) {
            val base = parse(ServerEndpoint.normalizeBaseUrl(server.baseUrl)) ?: continue
            if (strictMatch(service, base)) return server.id
            if (looseMatch(service, trimmed, base)) return server.id
        }
        return null
    }

    /** Rule 1: same origin plus a path-prefix relationship. */
    private fun strictMatch(service: ParsedUrl, base: ParsedUrl): Boolean =
        service.scheme == base.scheme &&
            service.host == base.host &&
            service.port == base.port &&
            pathPrefix(base.path, service.path)

    /** Rule 2: same host, compatible ports, base path contained in the raw service URL. */
    private fun looseMatch(service: ParsedUrl, rawServiceUrl: String, base: ParsedUrl): Boolean =
        base.path.isNotEmpty() &&
            service.host == base.host &&
            portsCompatible(service, base) &&
            containsAtSegmentBoundary(rawServiceUrl, base.path)

    /** `/radarr` is a prefix of `/radarr/movie/12` but not of `/radarr2`. */
    private fun pathPrefix(basePath: String, servicePath: String): Boolean = when {
        basePath.isEmpty() -> true
        basePath == servicePath -> true
        else -> servicePath.startsWith("$basePath/")
    }

    /** Explicit ports must agree when both sides state one; defaults never block a match. */
    private fun portsCompatible(service: ParsedUrl, base: ParsedUrl): Boolean =
        !service.explicitPort || !base.explicitPort || service.port == base.port

    /**
     * True when [path] occurs in [rawUrl] at a whole-segment boundary, so
     * `/radarr` matches `.../apps/radarr` and `.../radarr?q=1` but not the
     * sibling `.../radarr2`.
     */
    private fun containsAtSegmentBoundary(rawUrl: String, path: String): Boolean {
        var index = rawUrl.indexOf(path)
        while (index >= 0) {
            val after = index + path.length
            val boundary = after >= rawUrl.length ||
                rawUrl[after] == '/' ||
                rawUrl[after] == '?'
            if (boundary) return true
            index = rawUrl.indexOf(path, index + 1)
        }
        return false
    }

    private fun parse(raw: String): ParsedUrl? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val explicit = uri.port > 0
        val port = when {
            explicit -> uri.port
            scheme == "http" -> 80
            scheme == "https" -> 443
            else -> -1
        }
        return ParsedUrl(scheme, host, explicit, port, normalizePath(uri.rawPath))
    }

    /** Drops trailing slashes so `/radarr/` and `/radarr` compare equal; keeps the leading slash. */
    private fun normalizePath(rawPath: String?): String = (rawPath ?: "").trimEnd('/')

    private data class ParsedUrl(
        val scheme: String,
        val host: String,
        val explicitPort: Boolean,
        val port: Int,
        val path: String,
    )
}
