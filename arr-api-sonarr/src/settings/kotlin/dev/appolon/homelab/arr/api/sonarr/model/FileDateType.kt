package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr FileDateType schema. */
@Serializable
enum class FileDateType(val wireValue: String) {
    @SerialName("none")
    NONE("none"),

    @SerialName("localAirDate")
    LOCAL_AIR_DATE("localAirDate"),

    @SerialName("utcAirDate")
    UTC_AIR_DATE("utcAirDate");

    override fun toString(): String = wireValue
}
