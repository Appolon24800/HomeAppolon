package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueBulkResource(val ids: List<Int>? = null)
