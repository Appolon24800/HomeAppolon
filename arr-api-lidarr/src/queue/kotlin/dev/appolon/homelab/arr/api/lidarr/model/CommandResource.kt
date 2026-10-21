package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class CommandResource(
    val id: Int = 0,
    val name: String? = null,
    val commandName: String? = null,
    val message: String? = null,
    val body: Command? = null,
    val priority: CommandPriority = CommandPriority.NORMAL,
    val status: CommandStatus = CommandStatus.QUEUED,
    val result: CommandResult = CommandResult.UNKNOWN,
    val queued: String = "",
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
