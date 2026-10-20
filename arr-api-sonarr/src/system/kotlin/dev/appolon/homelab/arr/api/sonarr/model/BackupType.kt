package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr BackupType schema. */
@Serializable
enum class BackupType(val wireValue: String) {
    @SerialName("scheduled")
    SCHEDULED("scheduled"),

    @SerialName("manual")
    MANUAL("manual"),

    @SerialName("update")
    UPDATE("update");

    override fun toString(): String = wireValue
}
