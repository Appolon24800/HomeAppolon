package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import java.net.URI
import java.net.URLDecoder

/**
 * Parses links that carry a server URL plus an API key, so tapping one
 * (from a QR code, a password manager, a shared chat message, or the
 * dashboard's own domain) prefills the server setup sheet instead of
 * forcing a manual copy-paste of the key.
 *
 * Recognized shapes:
 * 1. Explicit connect link: `homelab://arr/add?app=radarr&url=http://host:port/radarr&apikey=KEY`
 *    The same query shape on an https page URL also matches, so a link
 *    like `https://home.appolon.dev/arr/add?app=radarr&url=...&apikey=KEY`
 *    works identically.
 * 2. Any http(s) URL whose query (or fragment) carries an apikey-family
 *    parameter: the key is lifted out and the remaining URL becomes the
 *    server base URL. Example:
 *    `http://192.168.1.39:30025/radarr/api/v3/health?apikey=KEY`
 *    parses to base `http://192.168.1.39:30025/radarr`.
 *
 * The app type is derived from the `app`/`type` parameter when present,
 * otherwise from the first host label or path segment that names one of
 * the five apps (radarr, sonarr, lidarr, prowlarr, bazarr).
 */
object ArrLinkParser {

    /** Query parameter names treated as API key carriers, lowercased. */
    private val KEY_PARAMS = setOf("apikey", "api_key", "x-api-key")

    /** Path segments that are API surfaces, not part of the base URL. */
    private val API_SEGMENTS = setOf("api", "v3", "v1", "ping", "login", "content", "feed", "signalr", "health", "system", "status")

    data class Result(
        val appType: AppType?,
        val baseUrl: String,
        val apiKey: String,
    )

    fun parse(raw: String): Result? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null

        val isConnectLink = scheme == "homelab" && uri.host?.equals("arr", ignoreCase = true) == true
        val isHttpLink = scheme == "http" || scheme == "https"
        if (!isConnectLink && !isHttpLink) return null

        val params = parameters(uri)
        val apiKey = KEY_PARAMS.firstNotNullOfOrNull { params[it] }?.takeIf { it.isNotBlank() }
            ?: return null

        val explicitUrl = params["url"] ?: params["server"] ?: params["base"]
        val appParam = params["app"] ?: params["type"]

        return if (isConnectLink || explicitUrl != null || appParam != null) {
            // Shape 1: connect link; the base URL comes from a parameter.
            val target = explicitUrl ?: return null
            val cleaned = stripKeyParams(target)
            Result(
                appType = appTypeFromName(appParam)
                    ?: appTypeFromUrl(cleaned)
                    ?: return null,
                baseUrl = normalizeBase(cleaned),
                apiKey = apiKey,
            )
        } else {
            // Shape 2: the link itself is the server URL plus a key.
            val cleaned = stripKeyParams(trimmed)
            Result(
                appType = appTypeFromUrl(cleaned) ?: return null,
                baseUrl = normalizeBase(trimApiSegments(cleaned)),
                apiKey = apiKey,
            )
        }
    }

    /** Query and fragment parameters, lowercased keys, decoded values. */
    private fun parameters(uri: URI): Map<String, String> {
        val map = mutableMapOf<String, String>()
        fun absorb(raw: String?) {
            if (raw.isNullOrBlank()) return
            raw.split('&').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val key = pair.substring(0, idx).lowercase()
                    val value = runCatching {
                        URLDecoder.decode(pair.substring(idx + 1), Charsets.UTF_8)
                    }.getOrDefault(pair.substring(idx + 1))
                    if (key !in map) map[key] = value
                }
            }
        }
        absorb(uri.rawQuery)
        absorb(uri.rawFragment)
        return map
    }

    /** Rebuilds the URL string without any apikey-family query parameter. */
    private fun stripKeyParams(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val raw = uri.rawQuery ?: return url
        val kept = raw.split('&').filter {
            val key = it.substringBefore('=').lowercase()
            it.isNotEmpty() && key !in KEY_PARAMS
        }
        if (kept.size == raw.split('&').count { it.isNotEmpty() }) return url
        var rebuilt = buildString {
            append(uri.scheme).append("://").append(uri.rawAuthority).append(uri.rawPath ?: "")
            if (kept.isNotEmpty()) append('?').append(kept.joinToString("&"))
            uri.rawFragment?.let { append('#').append(it) }
        }
        return rebuilt
    }

    /** Drops trailing API surface segments so the base URL is a server root. */
    private fun trimApiSegments(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() } ?: return url
        var cut = segments.size
        outer@ for (i in segments.indices) {
            val seg = segments[i].lowercase()
            if (seg in API_SEGMENTS) {
                // "api" only starts a surface when followed by v1/v3 or nothing meaningful
                if (seg == "api" && i + 1 < segments.size && segments[i + 1].lowercase() !in setOf("v1", "v3")) {
                    continue@outer
                }
                cut = i
                break
            }
            // A single generic tail like /health or /status only trims when last.
            if (seg in setOf("health", "status", "ping") && i == segments.size - 1) {
                cut = i
                break
            }
        }
        val kept = segments.subList(0, cut)
        val path = if (kept.isEmpty()) "" else "/" + kept.joinToString("/")
        return buildString {
            append(uri.scheme).append("://").append(uri.rawAuthority).append(path)
        }
    }

    private fun appTypeFromName(name: String?): AppType? {
        if (name.isNullOrBlank()) return null
        val cleaned = name.trim().lowercase()
        return AppType.entries.firstOrNull { it.wireName == cleaned }
    }

    /** Finds the app type from the first host label or path segment. */
    private fun appTypeFromUrl(url: String): AppType? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val hostLabels = uri.host?.split('.') ?: emptyList()
        val candidates = hostLabels.map { it.lowercase() } +
            (uri.path?.trim('/')?.split('/') ?: emptyList()).map { it.lowercase() }
        return candidates.firstNotNullOfOrNull { label ->
            AppType.entries.firstOrNull { it.wireName == label }
        }
    }

    /** Finds the arr app a plain URL points at, when it names one. */
    fun appTypeForUrl(url: String): AppType? = appTypeFromUrl(url)

    /** Applies the same normalization the setup sheet uses for base URLs. */
    private fun normalizeBase(url: String): String =
        dev.appolon.homelab.arr.net.ServerEndpoint.normalizeBaseUrl(url)
}
