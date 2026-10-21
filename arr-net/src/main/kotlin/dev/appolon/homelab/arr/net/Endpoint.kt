package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import kotlinx.serialization.Serializable

/**
 * Proxy configuration for one server. Resolution order is server override
 * (when [isConfigured]), then the global default, then direct.
 */
@Serializable
data class ProxyConfig(
    val type: Type = Type.NONE,
    val host: String = "",
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
    /** When true, resolve to Orbot's SOCKS5 endpoint after an install probe. */
    val useOrbot: Boolean = false,
) {
    enum class Type { NONE, HTTP, SOCKS5 }

    val isConfigured: Boolean get() = type != Type.NONE || useOrbot
}

/** TLS acceptance policy for one server. */
@Serializable
sealed interface TrustSpec {
    /** Default system trust. */
    @Serializable
    data object System : TrustSpec

    /** Accept self-signed certificates from this server. */
    @Serializable
    data object AcceptAny : TrustSpec

    /** Accept self-signed but pin the SHA-256 fingerprint of the leaf cert. */
    @Serializable
    data class Pinned(val sha256FingerprintHex: String) : TrustSpec
}

/** Everything needed to reach one *arr instance. */
data class ServerEndpoint(
    val serverId: ServerId,
    val baseUrl: String,
    val apiKey: String,
    val basicAuthUser: String? = null,
    val basicAuthPassword: String? = null,
    val proxy: ProxyConfig? = null,
    val trust: TrustSpec = TrustSpec.System,
) {
    companion object {
        /**
         * Normalizes user-entered URLs: adds https when no scheme is present,
         * keeps any path prefix (example: http://host:30025/radarr), guarantees
         * a trailing slash for Retrofit base URLs.
         */
        fun normalizeBaseUrl(raw: String): String = raw.trim().let { url ->
            val withScheme = when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                else -> "http://$url"
            }.trimEnd('/')
            "$withScheme/"
        }
    }
}

/**
 * Builds and caches one OkHttp client per server endpoint. Implement in :arr-net.
 */
interface ArrClientFactory {
    /**
     * Authenticated client for API traffic. Credentials (API key, basic auth)
     * are scoped to the endpoint's own origin, so cross-origin redirect
     * follow-ups and third-party hosts never receive them.
     */
    fun clientFor(endpoint: ServerEndpoint): okhttp3.OkHttpClient

    /**
     * Client without credentials for remote artwork and other non-API hosts,
     * sharing the endpoint's proxy and TLS policy. Image loaders must use
     * this, not [clientFor], so API keys are never stamped onto third-party
     * requests. Defaults to [clientFor] so simple fakes keep compiling;
     * [DefaultArrClientFactory] overrides it with a credential-free build.
     */
    fun imageClientFor(endpoint: ServerEndpoint): okhttp3.OkHttpClient = clientFor(endpoint)

    /** Clears cached clients, for example after a proxy or trust change. */
    fun invalidate(serverId: ServerId)
}
