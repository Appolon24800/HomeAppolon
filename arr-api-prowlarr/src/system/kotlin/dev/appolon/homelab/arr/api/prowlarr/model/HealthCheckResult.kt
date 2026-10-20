package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Severity of one health check, from the spec's HealthCheckResult enum.
 * [toString] returns the wire name because Retrofit serializes @Query values
 * through the enum's toString fallback, while bodies use @SerialName.
 */
@Serializable
enum class HealthCheckResult(private val wireName: String) {
    @SerialName("ok") OK("ok"),
    @SerialName("notice") NOTICE("notice"),
    @SerialName("warning") WARNING("warning"),
    @SerialName("error") ERROR("error");

    override fun toString(): String = wireName
}
