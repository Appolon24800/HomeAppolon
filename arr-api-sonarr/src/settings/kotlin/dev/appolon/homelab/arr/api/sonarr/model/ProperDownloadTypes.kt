package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr ProperDownloadTypes schema. */
@Serializable
enum class ProperDownloadTypes(val wireValue: String) {
    @SerialName("preferAndUpgrade")
    PREFER_AND_UPGRADE("preferAndUpgrade"),

    @SerialName("doNotUpgrade")
    DO_NOT_UPGRADE("doNotUpgrade"),

    @SerialName("doNotPrefer")
    DO_NOT_PREFER("doNotPrefer");

    override fun toString(): String = wireValue
}
