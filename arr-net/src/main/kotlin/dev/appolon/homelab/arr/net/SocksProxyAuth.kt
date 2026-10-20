package dev.appolon.homelab.arr.net

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttp's `proxyAuthenticator` only covers HTTP proxy challenges (407 on
 * CONNECT). SOCKS5 username/password authentication happens inside
 * `java.net.Socket`, which consults the process-wide default
 * [Authenticator] and nothing else. This registry installs one shared
 * authenticator that answers exclusively for proxy requests addressed to a
 * registered proxy host and port, keyed the same way the JDK keys its lookup,
 * so other traffic (and other apps' authenticators) is left alone. Entries are
 * reference-counted by the factory so invalidating the last server using a
 * proxy drops its credentials.
 */
internal object SocksProxyAuth {
    private val credentials = ConcurrentHashMap<String, PasswordAuthentication>()
    private val refCounts = ConcurrentHashMap<String, Int>()

    @Volatile
    private var installed = false

    fun register(host: String, port: Int, username: String, password: String) {
        val key = key(host, port)
        credentials[key] = PasswordAuthentication(username, password.toCharArray())
        refCounts.merge(key, 1, Int::plus)
        if (!installed) {
            synchronized(this) {
                if (!installed) {
                    // The JDK raises SOCKS auth requests with RequestorType.SERVER
                    // despite them being proxy logins, so the (host, port) key
                    // is the discriminator, not the requestor type.
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication? {
                            val host = requestingHost ?: requestingSite?.hostAddress ?: return null
                            return credentials[key(host, requestingPort)]
                        }
                    })
                    installed = true
                }
            }
        }
    }

    fun unregister(host: String, port: Int) {
        val key = key(host, port)
        val remaining = refCounts.merge(key, -1, Int::plus) ?: 0
        if (remaining <= 0) {
            refCounts.remove(key)
            credentials.remove(key)
        }
    }

    private fun key(host: String, port: Int): String = "$host:$port"
}
