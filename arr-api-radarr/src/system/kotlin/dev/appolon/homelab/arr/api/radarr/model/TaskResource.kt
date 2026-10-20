package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr TaskResource. Dates remain unparsed strings.
 * `lastDuration` is a .NET date-span string, parsed by the UI layer only. */
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
