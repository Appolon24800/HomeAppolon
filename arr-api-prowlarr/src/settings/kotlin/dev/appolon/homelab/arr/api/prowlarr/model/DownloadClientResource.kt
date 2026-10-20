package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One download client definition (from the schema endpoint) or configured
 * instance. Field rows and provider messages reuse the shared provider
 * models, and the protocol reuses the spec's DownloadProtocol enum already
 * modeled for indexers.
 */
@Serializable
data class DownloadClientResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: List<Int>? = null,
    val presets: List<DownloadClientResource>? = null,
    val enable: Boolean = false,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val priority: Int = 0,
    val categories: List<DownloadClientCategory>? = null,
    val supportsCategories: Boolean = false,
)
