package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr DownloadClientConfigResource wire resource for download client settings. */
@Serializable
data class DownloadClientConfigResource(
    val id: Int = 0,
    val downloadClientWorkingFolders: String? = null,
    val enableCompletedDownloadHandling: Boolean = false,
    val autoRedownloadFailed: Boolean = false,
    val autoRedownloadFailedFromInteractiveSearch: Boolean = false,
)
