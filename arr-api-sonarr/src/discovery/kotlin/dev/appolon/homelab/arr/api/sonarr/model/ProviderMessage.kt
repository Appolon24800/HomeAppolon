package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ProviderMessage wire contract, surfaced by import list test runs. */
@Serializable
data class ProviderMessage(
    val message: String? = null,
    val type: ProviderMessageType = ProviderMessageType.INFO
)
