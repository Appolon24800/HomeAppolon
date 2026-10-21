package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import kotlinx.serialization.Serializable

/** Proxy configuration for one server, resolved against the global default. */
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

/** Builds and caches one OkHttp client per server endpoint. Implement in :arr-net. */
interface ArrClientFactory {
    fun clientFor(endpoint: ServerEndpoint): okhttp3.OkHttpClient

    /** Clears cached clients, for example after a proxy or trust change. */
    fun invalidate(serverId: ServerId)
}
