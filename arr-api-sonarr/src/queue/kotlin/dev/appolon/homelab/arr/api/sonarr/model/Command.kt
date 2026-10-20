package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr Command wire contract. Dates remain unparsed strings. */
@Serializable
data class Command(
    val sendUpdatesToClient: Boolean = false,
    val updateScheduledTask: Boolean = false,
    val completionMessage: String? = null,
    val requiresDiskAccess: Boolean = false,
    val isExclusive: Boolean = false,
    val isLongRunning: Boolean = false,
    val name: String? = null,
    val lastExecutionTime: String? = null,
    val lastStartTime: String? = null,
    val trigger: CommandTrigger = CommandTrigger.UNSPECIFIED,
    val suppressMessages: Boolean = false,
    val clientUserAgent: String? = null
)
