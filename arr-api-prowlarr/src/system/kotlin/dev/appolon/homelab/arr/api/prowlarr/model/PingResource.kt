package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/** Liveness answer from `GET /ping`; `status` is "OK" on a healthy server. */
@Serializable
data class PingResource(
    val status: String? = null,
)
