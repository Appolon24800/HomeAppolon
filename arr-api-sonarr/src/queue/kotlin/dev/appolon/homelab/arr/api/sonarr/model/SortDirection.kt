package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr SortDirection schema. */
@Serializable
enum class SortDirection(val wireValue: String) {
    @SerialName("default")
    DEFAULT("default"),
    @SerialName("ascending")
    ASCENDING("ascending"),
    @SerialName("descending")
    DESCENDING("descending");

    override fun toString(): String = wireValue
}
