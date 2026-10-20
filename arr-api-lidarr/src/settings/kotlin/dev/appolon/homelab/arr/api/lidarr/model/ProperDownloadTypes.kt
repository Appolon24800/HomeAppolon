package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class ProperDownloadTypes {
    @SerialName("preferAndUpgrade") PREFER_AND_UPGRADE,
    @SerialName("doNotUpgrade") DO_NOT_UPGRADE,
    @SerialName("doNotPrefer") DO_NOT_PREFER;

    override fun toString(): String = when (this) {
        PREFER_AND_UPGRADE -> "preferAndUpgrade"
        DO_NOT_UPGRADE -> "doNotUpgrade"
        DO_NOT_PREFER -> "doNotPrefer"
    }
}
