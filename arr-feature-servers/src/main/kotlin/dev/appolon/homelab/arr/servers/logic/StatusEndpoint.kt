package dev.appolon.homelab.arr.servers.logic

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.ServerEndpoint
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves the system-status endpoint of each *arr app and reads the version
 * out of its response. The API generation differs per app, so the path must
 * follow the app type: Radarr and Sonarr are v3, Lidarr and Prowlarr v1,
 * Bazarr ships an unversioned prefix.
 */
object StatusEndpoint {

    /** Status path for one app type, always rooted and slash-prefixed. */
    fun pathFor(appType: AppType): String = when (appType) {
        AppType.RADARR, AppType.SONARR -> "/api/v3/system/status"
        AppType.LIDARR, AppType.PROWLARR -> "/api/v1/system/status"
        AppType.BAZARR -> "/api/system/status"
    }

    /**
     * Absolute status URL for one server: the normalized base URL (path
     * prefixes like `/radarr` preserved) joined with the app's status path.
     */
    fun urlFor(baseUrl: String, appType: AppType): String =
        ServerEndpoint.normalizeBaseUrl(baseUrl).trimEnd('/') + pathFor(appType)

    /**
     * Reads the `version` member of a status response body. Returns null for
     * blank, malformed, or version-less payloads (including JSON null and
     * non-strings) instead of throwing: a reachability check still succeeds
     * when the server omits the field.
     */
    fun extractVersion(responseBody: String): String? = runCatching {
        val primitive = ArrJson.parseToJsonElement(responseBody).jsonObject["version"]
            ?.jsonPrimitive
        if (primitive is JsonNull) null else primitive?.content
    }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}
