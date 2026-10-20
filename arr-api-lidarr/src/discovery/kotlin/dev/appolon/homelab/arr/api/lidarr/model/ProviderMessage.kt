package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ProviderMessage wire resource carried by import list providers. */
@Serializable
data class ProviderMessage(
    val message: String? = null,
    val type: ProviderMessageType? = null,
)
