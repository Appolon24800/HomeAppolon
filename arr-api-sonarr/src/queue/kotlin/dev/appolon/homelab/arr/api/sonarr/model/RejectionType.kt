package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr RejectionType schema. */
@Serializable
enum class RejectionType(val wireValue: String) {
    @SerialName("permanent")
    PERMANENT("permanent"),
    @SerialName("temporary")
    TEMPORARY("temporary");

    override fun toString(): String = wireValue
}
