package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SortDirection {
    @SerialName("default") DEFAULT,
    @SerialName("ascending") ASCENDING,
    @SerialName("descending") DESCENDING;

    override fun toString(): String = name.lowercase()
}
