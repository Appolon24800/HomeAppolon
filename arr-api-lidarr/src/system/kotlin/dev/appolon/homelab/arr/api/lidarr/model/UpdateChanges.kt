package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr UpdateChanges wire resource listing changelog entries per kind. */
@Serializable
data class UpdateChanges(
    val new: List<String>? = null,
    val fixed: List<String>? = null,
)
