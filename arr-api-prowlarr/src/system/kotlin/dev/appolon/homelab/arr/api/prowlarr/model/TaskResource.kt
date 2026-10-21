package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One scheduled server job from `GET /api/v1/system/task`. Timestamps stay
 * wire-format strings and `lastDuration` is a .NET date-span string; the UI
 * parses both with :arr-core ArrTime.
 */
@Serializable
data class TaskResource(
    val id: Int = 0,
    val name: String? = null,
    val taskName: String? = null,
    val interval: Int = 0,
    val lastExecution: String? = null,
    val lastStartTime: String? = null,
    val nextExecution: String? = null,
    val lastDuration: String? = null,
)
