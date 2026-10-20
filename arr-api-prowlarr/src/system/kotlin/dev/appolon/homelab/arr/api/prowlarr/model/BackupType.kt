package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What triggered a backup, from the spec's BackupType enum. [toString]
 * returns the wire name because Retrofit serializes @Query values through
 * the enum's toString fallback, while bodies use @SerialName.
 */
@Serializable
enum class BackupType(private val wireName: String) {
    @SerialName("scheduled") SCHEDULED("scheduled"),
    @SerialName("manual") MANUAL("manual"),
    @SerialName("update") UPDATE("update");

    override fun toString(): String = wireName
}
