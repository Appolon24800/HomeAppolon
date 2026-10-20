package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr TaskResource wire resource for scheduled jobs. Execution timestamps
 * and the read-only LastDuration date-span stay unparsed strings.
 */
@Serializable
data class TaskResource(
    val id: Int = 0,
    val name: String? = null,
    val taskName: String? = null,
    val interval: Int = 0,
    val lastExecution: String = "",
    val lastStartTime: String = "",
    val nextExecution: String = "",
    val lastDuration: String = "",
)
