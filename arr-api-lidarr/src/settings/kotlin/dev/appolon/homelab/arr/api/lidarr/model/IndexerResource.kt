package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr IndexerResource wire resource. Reuses [Field] from the library group. */
@Serializable
data class IndexerResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: Set<Int>? = null,
    val presets: List<IndexerResource>? = null,
    val enableRss: Boolean = false,
    val enableAutomaticSearch: Boolean = false,
    val enableInteractiveSearch: Boolean = false,
    val supportsRss: Boolean = false,
    val supportsSearch: Boolean = false,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val priority: Int = 0,
    val downloadClientId: Int = 0,
)
