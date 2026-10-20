package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr SeasonPackUpgradeType schema. */
@Serializable
enum class SeasonPackUpgradeType(val wireValue: String) {
    @SerialName("all")
    ALL("all"),

    @SerialName("threshold")
    THRESHOLD("threshold"),

    @SerialName("any")
    ANY("any");

    override fun toString(): String = wireValue
}
