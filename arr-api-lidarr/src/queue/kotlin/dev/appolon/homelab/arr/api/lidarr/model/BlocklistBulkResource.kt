package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class BlocklistBulkResource(val ids: List<Int>? = null)
