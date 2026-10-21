package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Queue priority of a command, from the spec's CommandPriority enum. */
@Serializable
enum class CommandPriority {
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
    @SerialName("low") LOW,
}

/** Lifecycle state of a command, from the spec's CommandStatus enum. */
@Serializable
enum class CommandStatus {
    @SerialName("queued") QUEUED,
    @SerialName("started") STARTED,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("aborted") ABORTED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("orphaned") ORPHANED,
}

/** What started a command, from the spec's CommandTrigger enum. */
@Serializable
enum class CommandTrigger {
    @SerialName("unspecified") UNSPECIFIED,
    @SerialName("manual") MANUAL,
    @SerialName("scheduled") SCHEDULED,
}

/**
 * Command payload posted to and echoed by the command endpoint. The
 * readOnly flags are server-computed; callers set execution options and let
 * the server fill the rest.
 */
@Serializable
data class Command(
    val sendUpdatesToClient: Boolean = false,
    val updateScheduledTask: Boolean = false,
    val completionMessage: String? = null,
    val requiresDiskAccess: Boolean = false,
    val isExclusive: Boolean = false,
    val isTypeExclusive: Boolean = false,
    val name: String? = null,
    val lastExecutionTime: String? = null,
    val lastStartTime: String? = null,
    val trigger: CommandTrigger = CommandTrigger.UNSPECIFIED,
    val suppressMessages: Boolean = false,
    val clientUserAgent: String? = null,
)
