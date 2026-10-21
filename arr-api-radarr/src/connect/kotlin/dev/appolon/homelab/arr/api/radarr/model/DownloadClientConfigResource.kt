package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr DownloadClientConfigResource.
 * Dates remain unparsed strings. */
@Serializable
data class DownloadClientConfigResource(
    val id: Int = 0,
    val downloadClientWorkingFolders: String? = null,
    val enableCompletedDownloadHandling: Boolean = false,
    val checkForFinishedDownloadInterval: Int = 0,
    val autoRedownloadFailed: Boolean = false,
    val autoRedownloadFailedFromInteractiveSearch: Boolean = false,
)
