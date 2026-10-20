package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One indexer proxy (FlareSolverr-style request detour). The spec lists
 * notification-derived flags ([onHealthIssue], [includeHealthWarnings]) and
 * [link] on this resource; they are modeled verbatim because the bundled spec
 * is the contract.
 */
@Serializable
data class IndexerProxyResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: List<Int>? = null,
    val presets: List<IndexerProxyResource>? = null,
    val link: String? = null,
    val onHealthIssue: Boolean = false,
    val supportsOnHealthIssue: Boolean = false,
    val includeHealthWarnings: Boolean = false,
    val testCommand: String? = null,
)
