package dev.appolon.homelab.arr.sync

import dev.appolon.homelab.arr.core.ServerId

/** Live and polled events from one server, fanned out on a SharedFlow. */
sealed interface ArrEvent {
    val serverId: ServerId

    /** Generic entity-invalidated signal from SignalR or Bazarr sockets. */
    data class EntitiesChanged(
        override val serverId: ServerId,
        val entity: String,
        val action: String?,
    ) : ArrEvent

    data class QueueChanged(override val serverId: ServerId) : ArrEvent

    data class CalendarChanged(override val serverId: ServerId) : ArrEvent

    data class HealthChanged(override val serverId: ServerId) : ArrEvent

    data class DiskSpaceChanged(override val serverId: ServerId) : ArrEvent

    /** Emitted by the sync scheduler when a background check produced a notification. */
    data class NotificationIssued(
        override val serverId: ServerId,
        val channel: String,
        val title: String,
        val body: String,
    ) : ArrEvent
}

/** Connection state of one server's live socket. */
enum class LiveConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RETRYING }
