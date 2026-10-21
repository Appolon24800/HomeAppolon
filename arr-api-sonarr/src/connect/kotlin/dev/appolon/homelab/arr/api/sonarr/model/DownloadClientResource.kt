package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/**
 * Sonarr download client provider. `fields` carries the provider-specific
 * settings contract; values arrive as arbitrary JSON keyed by `field.name`.
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
    val tags: Set<Int>? = null,
    val presets: List<DownloadClientResource>? = null,
    val enable: Boolean = false,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val priority: Int = 0,
    val removeCompletedDownloads: Boolean = false,
    val removeFailedDownloads: Boolean = false
)
