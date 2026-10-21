package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr BackupType wire enum, constants in spec order. */
@Serializable
enum class BackupType {
    @SerialName("scheduled") SCHEDULED,
    @SerialName("manual") MANUAL,
    @SerialName("update") UPDATE,
    ;

    override fun toString(): String = when (this) {
        SCHEDULED -> "scheduled"
        MANUAL -> "manual"
        UPDATE -> "update"
    }
}
