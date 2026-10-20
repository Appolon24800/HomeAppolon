package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Production [ArrClientFactory]. Derives one client per server from a shared
 * base, so every server gets its own interceptors, proxy, and TLS policy while
 * sharing one dispatcher and connection pool. Results are cached per
 * [ServerId] and keyed by a fingerprint of the client-affecting fields, so
 * editing a server's key, auth, proxy, or trust rebuilds its client without a
 * manual invalidate. SOCKS proxy credentials are handed to
 * [SocksProxyAuth] because that is the only path `java.net.Socket` consults.
 */
class DefaultArrClientFactory(
    private val orbotDetector: OrbotDetector = OrbotDetector(),
    base: OkHttpClient = OkHttpClient(),
) : ArrClientFactory {
    private val baseClient = base
    private val cache = ConcurrentHashMap<ServerId, CachedClient>()

    override fun clientFor(endpoint: ServerEndpoint): OkHttpClient {
        val proxy = orbotDetector.resolveProxy(endpoint.proxy ?: ProxyConfig())
        val fingerprint = fingerprint(endpoint, proxy)
        cache[endpoint.serverId]?.let { cached ->
            if (cached.fingerprint == fingerprint) return cached.client
        }
        val fresh = CachedClient(fingerprint, buildClient(endpoint, proxy), proxy)
        // Concurrent build: prefer whichever client lands first for stability.
        val existing = cache.putIfAbsent(endpoint.serverId, fresh)
        if (existing != null && existing.fingerprint == fingerprint) return existing.client
        replaceCached(endpoint.serverId, existing, fresh)
        return fresh.client
    }

    override fun invalidate(serverId: ServerId) {
        replaceCached(serverId, cache.remove(serverId), null)
    }

    private fun replaceCached(serverId: ServerId, previous: CachedClient?, next: CachedClient?) {
        previous?.registeredSocksCredentials()?.let { (address, _, _) ->
            SocksProxyAuth.unregister(address.first, address.second)
        }
        next?.registeredSocksCredentials()?.let { (address, username, password) ->
            SocksProxyAuth.register(address.first, address.second, username, password)
        }
        if (next != null) cache[serverId] = next
    }

    private fun buildClient(endpoint: ServerEndpoint, proxy: ProxyConfig): OkHttpClient =
        baseClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(
                HeadersInterceptor(
                    endpoint.apiKey,
                    endpoint.basicAuthUser,
                    endpoint.basicAuthPassword,
                ),
            )
            .apply {
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
 * Stamps every request with the server's identity headers. `header` (not `add`)
 * so a rogue upstream redirect cannot stack a second key or authorization.
 */
private class HeadersInterceptor(
    private val apiKey: String,
    private val basicAuthUser: String?,
    private val basicAuthPassword: String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (!basicAuthUser.isNullOrEmpty() && !basicAuthPassword.isNullOrEmpty()) {
                    header("Authorization", Credentials.basic(basicAuthUser, basicAuthPassword))
                }
            }
            .build()
        return chain.proceed(request)
    }
}
