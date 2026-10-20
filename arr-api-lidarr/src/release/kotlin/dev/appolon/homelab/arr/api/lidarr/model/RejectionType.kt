package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RejectionType {
    @SerialName("permanent") PERMANENT,
    @SerialName("temporary") TEMPORARY,
}
