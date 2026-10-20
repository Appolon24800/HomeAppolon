package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One server's live event socket. Implementations own their transport, map
 * wire events to [ArrEvent], and keep reconnecting on their own until [stop].
 * [start] returns once the reconnect loop is launched, not once connected;
 * watch [state] for the transition.
 */
interface LiveConnection {
    val serverId: ServerId
    val appType: AppType

    /** Connection state; settles on DISCONNECTED after [stop] completes. */
    val state: StateFlow<LiveConnectionState>

    /** Hot flow of mapped events, live only while started. No replay. */
    val events: Flow<ArrEvent>

    /** Idempotent: launches the connect/reconnect loop in the given scope. */
    suspend fun start()

    /** Idempotent: closes the socket and stops reconnecting. */
    suspend fun stop()
}

/**
 * Everything needed to open one server's live socket. The endpoint carries the
 * plaintext API key, so this value is as sensitive as the key itself and must
 * never be logged.
 */
data class LiveServerSpec(
    val endpoint: ServerEndpoint,
    val appType: AppType,
) {
    val serverId: ServerId get() = endpoint.serverId
}

/** Builds the concrete connection for one server; the manager calls this per reconcile. */
fun interface LiveConnectionFactory {
    fun create(spec: LiveServerSpec): LiveConnection
}
