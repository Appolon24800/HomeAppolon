package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ProxyConfig

/** Assigns connection versions without including credentials in UI state. */
internal class ServerConnectionVersions {
    private var nextVersion = 0L
    private var previous = emptyMap<ServerId, Pair<ArrServer, Long>>()

    fun update(servers: List<ArrServer>, defaultProxy: ProxyConfig?): Map<ServerId, Long> {
        previous = servers.associate { server ->
            val connection = server.copy(
                name = "",
                order = 0,
                createdAtMillis = 0,
                proxy = server.proxy ?: defaultProxy,
            )
            val old = previous[server.id]
            val version = if (old?.first == connection) old.second else ++nextVersion
            server.id to (connection to version)
        }
        return previous.mapValues { it.value.second }
    }
}
