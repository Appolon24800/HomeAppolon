package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr TaskResource wire contract. Dates remain unparsed strings. */
@Serializable
data class TaskResource(
    val id: Int = 0,
    val name: String? = null,
    val taskName: String? = null,
    val interval: Int = 0,
    val lastExecution: String? = null,
    val lastStartTime: String? = null,
    val nextExecution: String? = null,
    val lastDuration: String? = null
)
