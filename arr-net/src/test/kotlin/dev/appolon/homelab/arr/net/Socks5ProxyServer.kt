package dev.appolon.homelab.arr.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * Minimal in-process SOCKS5 server speaking RFC 1928 (CONNECT) and RFC 1929
 * (username/password auth) — enough for OkHttp's `Socket(proxy)` client. It
 * records what the client asked for: address type 3 (domain name) proves the
 * client delegated DNS to the proxy instead of resolving locally.
 */
class Socks5ProxyServer(
    private val requiredUsername: String? = null,
    private val requiredPassword: String? = null,
) : Closeable {
    data class Target(val addressType: Int, val host: String, val port: Int) {
        val isDomainName: Boolean get() = addressType == ATYP_DOMAIN
    }

    val requestedTargets = ConcurrentLinkedQueue<Target>()
    val authenticationAttempts = ConcurrentLinkedQueue<Pair<String, String>>()

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private var closed = false

    val port: Int get() = server.localPort

    fun start() {
        thread(name = "socks5-accept-${server.localPort}", isDaemon = true) {
            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                thread(name = "socks5-session", isDaemon = true) {
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
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        val version = input.read()
        check(version == 5) { "Only SOCKS5 is spoken here, got $version" }
        val methodCount = input.read()
        val offered = IntArray(methodCount) { input.read() }

        val authRequired = requiredUsername != null
        val chosen = when {
            !authRequired && NO_AUTH in offered -> NO_AUTH
            authRequired && USERNAME_PASSWORD in offered -> USERNAME_PASSWORD
            else -> NO_ACCEPTABLE_METHOD
        }
        output.write(byteArrayOf(5, chosen.toByte()))
        output.flush()
        if (chosen == NO_ACCEPTABLE_METHOD) {
            socket.closeQuietly()
            return
        }

        if (chosen == USERNAME_PASSWORD) {
            check(input.read() == 1) { "Expected RFC 1929 version 1" }
            val username = String(readCounted(input), Charsets.ISO_8859_1)
            val password = String(readCounted(input), Charsets.ISO_8859_1)
            authenticationAttempts += username to password
            val ok = username == requiredUsername && password == requiredPassword
            output.write(byteArrayOf(1, if (ok) 0 else 1))
            output.flush()
            if (!ok) {
                socket.closeQuietly()
                return
            }
        }

        check(input.read() == 5) { "Expected request version 5" }
        check(input.read() == CMD_CONNECT) { "Only CONNECT is supported" }
        input.read() // reserved
        val target = readTarget(input)
        requestedTargets += target

        val upstream = try {
            Socket().apply {
                connect(InetSocketAddress(InetAddress.getByName(target.host), target.port), 15_000)
                soTimeout = 15_000
            }
        } catch (_: IOException) {
            // Reply "host unreachable" so the client surfaces a connect failure.
            output.write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0))
            output.flush()
            socket.closeQuietly()
            return
        }

        // Success; bound address is irrelevant to the JDK client, zeros are fine.
        output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()

        relay(socket.getInputStream(), upstream.getOutputStream())
        relay(upstream.getInputStream(), socket.getOutputStream())
    }

    private fun readCounted(input: DataInputStream): ByteArray {
        val length = input.read()
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes
    }

    private fun readTarget(input: DataInputStream): Target {
        val addressType = input.read()
        val host = when (addressType) {
            ATYP_IPV4 -> (1..4).joinToString(".") { input.read().toString() }
            ATYP_DOMAIN -> String(readCounted(input), Charsets.ISO_8859_1)
            ATYP_IPV6 -> {
                val bytes = ByteArray(16)
                input.readFully(bytes)
                java.net.InetAddress.getByAddress(bytes).hostAddress ?: error("Unparseable IPv6")
            }

            else -> error("Unknown address type $addressType")
        }
        val port = input.readUnsignedShort()
        return Target(addressType, host, port)
    }

    private fun relay(from: InputStream, to: java.io.OutputStream) {
        thread(name = "socks5-relay", isDaemon = true) {
            try {
                from.copyTo(to)
                to.flush()
            } catch (_: IOException) {
                // Either end dropping the tunnel is a normal way for a request to end.
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

    private companion object {
        const val NO_AUTH = 0x00
        const val USERNAME_PASSWORD = 0x02
        const val NO_ACCEPTABLE_METHOD = 0xFF
        const val CMD_CONNECT = 0x01
        const val ATYP_IPV4 = 0x01
        const val ATYP_DOMAIN = 0x03
        const val ATYP_IPV6 = 0x04
    }
}
