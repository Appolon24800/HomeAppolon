package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Severity of a [ProviderMessage], from the spec's ProviderMessageType enum. */
@Serializable
enum class ProviderMessageType {
    @SerialName("info") INFO,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
}

/**
 * Provider-level status text that the server attaches to indexers, indexer
 * proxies and applications, usually as the result of a test or save.
 */
@Serializable
data class ProviderMessage(
    val message: String? = null,
    val type: ProviderMessageType = ProviderMessageType.INFO,
)
