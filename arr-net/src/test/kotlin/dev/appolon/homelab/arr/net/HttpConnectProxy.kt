package dev.appolon.homelab.arr.net

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * Minimal in-process forwarder speaking just enough HTTP proxy for OkHttp:
 * reads CONNECT requests (including the 407 challenge round OkHttp's
 * proxyAuthenticator drives), records the requested authority, then tunnels
 * bytes. The authority arrives as `host:port` text, proving target DNS stays
 * with the proxy.
 */
class HttpConnectProxy(
    private val requiredProxyAuthorization: String? = null,
) : Closeable {
    data class ConnectRequest(val authority: String, val proxyAuthorization: String?)

    val connectRequests = ConcurrentLinkedQueue<ConnectRequest>()

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private var closed = false

    val port: Int get() = server.localPort

    fun start() {
        thread(name = "http-proxy-accept-${server.localPort}", isDaemon = true) {
            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                thread(name = "http-proxy-session", isDaemon = true) {
                    try {
                        handle(socket)
                    } catch (_: IOException) {
                        socket.closeQuietly()
                    }
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val stream = InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1)
        val reader = BufferedReader(stream)
        var attempts = 0

        while (true) {
            val requestLine = reader.readLine() ?: return
            if (!requestLine.startsWith("CONNECT")) error("Expected CONNECT, got: $requestLine")
            val authority = requestLine.split(" ")[1]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: return
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                val name = line.substring(0, separator).trim().lowercase()
                headers[name] = line.substring(separator + 1).trim()
            }
            val authorization = headers["proxy-authorization"]
            connectRequests += ConnectRequest(authority, authorization)

            val authorized = when {
                requiredProxyAuthorization == null -> true
                else -> authorization == requiredProxyAuthorization
            }
            if (!authorized) {
                // Cap the challenge loop so a client that keeps offering bad
                // credentials gets the socket dropped instead of spinning.
                if (++attempts >= MAX_UNAUTHORIZED_ATTEMPTS) {
                    socket.closeQuietly()
                    return
                }
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                "Proxy-Authenticate: Basic realm=\"arr-test\"\r\n" +
                                "Content-Length: 0\r\n\r\n"
                            ).toByteArray(Charsets.ISO_8859_1),
                    )
                    flush()
                }
                // OkHttp answers the challenge with another CONNECT on this socket.
                continue
            }

            val upstreamHost = authority.substringBeforeLast(':')
            val upstreamPort = authority.substringAfterLast(':').toInt()
            val resolved = InetAddress.getByName(upstreamHost)
            val upstream = try {
                Socket().apply {
                    connect(InetSocketAddress(resolved, upstreamPort), 15_000)
                    soTimeout = 15_000
                }
            } catch (_: IOException) {
                socket.getOutputStream().apply {
                    write(
                        "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray(),
                    )
                    flush()
                }
                socket.closeQuietly()
                return
            }

            socket.getOutputStream().apply {
                write(
                    "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1),
                )
                flush()
            }
            relay(socket.getInputStream(), upstream.getOutputStream())
            relay(upstream.getInputStream(), socket.getOutputStream())
            return
        }
    }

    private fun relay(from: InputStream, to: java.io.OutputStream) {
        thread(name = "http-proxy-relay", isDaemon = true) {
            try {
                from.copyTo(to)
                to.flush()
            } catch (_: IOException) {
                // Tunnel teardown on either side ends the relay.
            } finally {
                try {
                    from.close()
                } catch (_: IOException) {
                    Unit
                }
            }
        }
    }

    override fun close() {
        closed = true
        server.close()
    }

    private fun Socket.closeQuietly() = try {
        close()
    } catch (_: IOException) {
        Unit
    }

    companion object {
        /** The `Proxy-Authorization` value the client should send. */
        fun basicAuthorization(username: String, password: String): String {
            val token = "$username:$password".toByteArray(Charsets.ISO_8859_1)
            return "Basic " + Base64.getEncoder().encodeToString(token)
        }

        private const val MAX_UNAUTHORIZED_ATTEMPTS = 3
    }
}
