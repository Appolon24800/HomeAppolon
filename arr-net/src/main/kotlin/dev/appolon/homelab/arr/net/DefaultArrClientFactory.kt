package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Production [ArrClientFactory]. Derives one client per server from a shared
 * base, so every server gets its own interceptors, proxy, and TLS policy while
 * sharing one dispatcher and connection pool. Results are cached per
 * [ServerId] (authenticated and image clients cache separately) and keyed by
 * a fingerprint of the client-affecting fields, so editing a server's key,
 * auth, proxy, or trust rebuilds its client without a manual invalidate. SOCKS
 * proxy credentials are handed to [SocksProxyAuth] because that is the only
 * path `java.net.Socket` consults.
 *
 * Proxy resolution is [ProxyConfig] override on the server, else the global
 * default from [globalProxy], else direct. The provider is consulted on every
 * lookup, so it must be a cheap cached snapshot: collect
 * `GlobalSettings.observeDefaultProxy` into a StateFlow and read it here,
 * never hit DataStore inline. Leaving the default `{ null }` in place makes
 * every server with no override route direct.
 */
class DefaultArrClientFactory(
    private val orbotDetector: OrbotDetector = OrbotDetector(),
    base: OkHttpClient = OkHttpClient(),
    private val globalProxy: () -> ProxyConfig? = { ArrNetworkDefaults.globalProxy() },
    private val cookieSource: CookieSource = CookieSource { ArrNetworkDefaults.cookieSource.cookiesFor(it) },
) : ArrClientFactory {
    private val baseClient = base
    private val cache = ConcurrentHashMap<CacheKey, CachedClient>()

    /**
     * Authenticated client for API traffic. Credentials are scoped to the
     * endpoint's own origin (scheme, host, port of its base URL); any other
     * origin, including cross-origin redirect follow-ups and third-party
     * artwork hosts, receives no API key or basic auth.
     */
    override fun clientFor(endpoint: ServerEndpoint): OkHttpClient =
        clientFor(endpoint, authenticated = true)

    /**
     * Credential-free sibling of [clientFor] for remote artwork and other
     * non-API hosts. Shares the endpoint's proxy and TLS policy; image
     * loaders must not ride the authenticated client.
     */
    override fun imageClientFor(endpoint: ServerEndpoint): OkHttpClient =
        clientFor(endpoint, authenticated = false)

    private fun clientFor(endpoint: ServerEndpoint, authenticated: Boolean): OkHttpClient {
        val proxy = effectiveProxy(endpoint)
        val fingerprint = fingerprint(endpoint, proxy)
        val cacheKey = CacheKey(endpoint.serverId, authenticated)
        cache[cacheKey]?.let { cached ->
            if (cached.fingerprint == fingerprint) return cached.client
        }
        val fresh = CachedClient(fingerprint, buildClient(endpoint, proxy, authenticated), proxy)
        // Concurrent build: prefer whichever client lands first for stability.
        val existing = cache.putIfAbsent(cacheKey, fresh)
        if (existing != null && existing.fingerprint == fingerprint) return existing.client
        replaceCached(cacheKey, existing, fresh)
        return fresh.client
    }

    override fun invalidate(serverId: ServerId) {
        replaceCached(CacheKey(serverId, true), cache.remove(CacheKey(serverId, true)), null)
        replaceCached(CacheKey(serverId, false), cache.remove(CacheKey(serverId, false)), null)
    }

    private fun replaceCached(key: CacheKey, previous: CachedClient?, next: CachedClient?) {
        previous?.registeredSocksCredentials()?.let { (address, _, _) ->
            SocksProxyAuth.unregister(address.first, address.second)
        }
        next?.registeredSocksCredentials()?.let { (address, username, password) ->
            SocksProxyAuth.register(address.first, address.second, username, password)
        }
        if (next != null) cache[key] = next
    }

    /**
     * Server override first (a [ProxyConfig] with [ProxyConfig.isConfigured]
     * true), then the global default from [globalProxy], then direct. A
     * per-server row of type NONE with no Orbot toggle is not an override, so
     * the global default still applies to it.
     *
     * Throws [OrbotUnavailableException] when a server demands Orbot that is
     * not installed: the request fails rather than routing direct.
     */
    private fun effectiveProxy(endpoint: ServerEndpoint): ProxyConfig {
        val override = endpoint.proxy?.takeIf { it.isConfigured }
        return orbotDetector.resolveProxy(override ?: globalProxy() ?: ProxyConfig())
    }

    private fun buildClient(endpoint: ServerEndpoint, proxy: ProxyConfig, authenticated: Boolean): OkHttpClient =
        baseClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                addNetworkInterceptor(
                    if (authenticated) {
                        HeadersInterceptor(
                            baseUrl = endpoint.baseUrl.toHttpUrlOrNull(),
                            apiKey = endpoint.apiKey,
                            basicAuthUser = endpoint.basicAuthUser,
                            basicAuthPassword = endpoint.basicAuthPassword,
                        )
                    } else {
                        UserAgentInterceptor
                    },
                )
                if (authenticated) {
                    addNetworkInterceptor(SessionCookieInterceptor(endpoint.baseUrl, cookieSource))
                }
                configureProxy(proxy)
                configureTrust(endpoint.trust)
            }
            .build()

    /**
     * Half-filled proxy rows (blank host or non-positive port) route direct
     * instead of failing every call: a mis-typed proxy must not take a server
     * offline. Explicit [Proxy.NO_PROXY] also keeps Android's system proxy out.
     */
    private fun OkHttpClient.Builder.configureProxy(proxy: ProxyConfig) {
        val usable = proxy.host.isNotBlank() && proxy.port > 0
        when {
            proxy.type == ProxyConfig.Type.HTTP && usable -> {
                val address = InetSocketAddress.createUnresolved(proxy.host.trim(), proxy.port)
                proxy(Proxy(Proxy.Type.HTTP, address))
                if (!proxy.username.isNullOrEmpty() && !proxy.password.isNullOrEmpty()) {
                    val user = proxy.username
                    val password = proxy.password
                    proxyAuthenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(user, password))
                            .build()
                    }
                }
            }

            proxy.type == ProxyConfig.Type.SOCKS5 && usable -> {
                val address = InetSocketAddress.createUnresolved(proxy.host.trim(), proxy.port)
                proxy(Proxy(Proxy.Type.SOCKS, address))
                // OkHttp never sees SOCKS credentials; registration happens in replaceCached.
            }

            else -> proxy(Proxy.NO_PROXY)
        }
    }

    private fun fingerprint(endpoint: ServerEndpoint, proxy: ProxyConfig): String = listOf(
        endpoint.apiKey,
        endpoint.basicAuthUser,
        endpoint.basicAuthPassword,
        proxy.type.name,
        proxy.host,
        proxy.port.toString(),
        proxy.username,
        proxy.password,
        endpoint.trust.toString(),
    ).joinToString("|")

    private data class CacheKey(val serverId: ServerId, val authenticated: Boolean)

    private class CachedClient(
        val fingerprint: String,
        val client: OkHttpClient,
        val proxy: ProxyConfig,
    )

    /** SOCKS credentials only count as registered when both fields are present. */
    private fun CachedClient.registeredSocksCredentials():
        Triple<Pair<String, Int>, String, String>? = with(proxy) {
            when {
                type != ProxyConfig.Type.SOCKS5 || host.isBlank() || port <= 0 -> null
                username.isNullOrEmpty() || password.isNullOrEmpty() -> null
                else -> Triple(host.trim() to port, username, password)
            }
        }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}

private const val USER_AGENT = "HomelabArr/1.0 (android)"

/**
 * Stamps the app user agent on every request; carries no credentials, so it
 * is safe on any origin.
 */
private object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
}

/**
 * Stamps the server's identity headers on same-origin requests only. The
 * endpoint's origin is the scheme, host, and port of its base URL; requests
 * to any other origin (cross-origin redirect follow-ups, third-party artwork
 * hosts) get the credential headers stripped. Runs as a network interceptor
 * on purpose: OkHttp builds redirect follow-ups by copying the prior
 * request's headers, so every hop must pass through this interceptor for the
 * cross-origin branch to remove what a same-origin hop added. A base URL that
 * cannot be parsed sends credentials nowhere (fail closed).
 */
private class HeadersInterceptor(
    private val baseUrl: HttpUrl?,
    private val apiKey: String,
    private val basicAuthUser: String?,
    private val basicAuthPassword: String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val rewritten = request.newBuilder()
            .header("User-Agent", USER_AGENT)
        if (request.url.sameOrigin(baseUrl)) {
            rewritten.header("X-Api-Key", apiKey)
            if (!basicAuthUser.isNullOrEmpty() && !basicAuthPassword.isNullOrEmpty()) {
                rewritten.header("Authorization", Credentials.basic(basicAuthUser, basicAuthPassword))
            }
        } else {
            rewritten.removeHeader("X-Api-Key")
            rewritten.removeHeader("Authorization")
        }
        return chain.proceed(rewritten.build())
    }
}

/** Origin equality on scheme, host, and port; [HttpUrl] normalizes default ports per scheme. */
internal fun HttpUrl.sameOrigin(other: HttpUrl?): Boolean =
    other != null && scheme == other.scheme && host == other.host && port == other.port
