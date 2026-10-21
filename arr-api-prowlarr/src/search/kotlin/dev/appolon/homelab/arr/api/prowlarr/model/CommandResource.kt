package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One queued or finished command. Timestamps stay wire-format strings parsed
 * by the UI with :arr-core ArrTime; [duration] is the server's date-span
 * string and passes through untouched.
 */
@Serializable
data class CommandResource(
    val id: Int = 0,
    val name: String? = null,
    val commandName: String? = null,
    val message: String? = null,
    val body: Command = Command(),
    val priority: CommandPriority = CommandPriority.NORMAL,
    val status: CommandStatus = CommandStatus.QUEUED,
    val queued: String? = null,
    val started: String? = null,
    val ended: String? = null,
    val duration: String? = null,
    val exception: String? = null,
    val trigger: CommandTrigger = CommandTrigger.UNSPECIFIED,
    val clientUserAgent: String? = null,
    val stateChangeTime: String? = null,
    val sendUpdatesToClient: Boolean = false,
    val updateScheduledTask: Boolean = false,
    val lastExecutionTime: String? = null,
)
