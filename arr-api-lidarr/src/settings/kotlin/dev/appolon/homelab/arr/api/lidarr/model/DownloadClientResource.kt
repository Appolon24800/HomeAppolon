package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr DownloadClientResource wire resource. Reuses [Field] from the library group. */
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
    val removeFailedDownloads: Boolean = false,
)
