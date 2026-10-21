package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Common command body defined by the spec. Server-specific command arguments are not declared. */
@Serializable
data class Command(
    val sendUpdatesToClient: Boolean = false,
    val updateScheduledTask: Boolean = false,
    val completionMessage: String? = null,
    val requiresDiskAccess: Boolean = false,
    val isExclusive: Boolean = false,
    val isTypeExclusive: Boolean = false,
    val isLongRunning: Boolean = false,
    val name: String? = null,
    val lastExecutionTime: String? = null,
    val lastStartTime: String? = null,
    val trigger: CommandTrigger = CommandTrigger.UNSPECIFIED,
    val suppressMessages: Boolean = false,
    val clientUserAgent: String? = null,
)
