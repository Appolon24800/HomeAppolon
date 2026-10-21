package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Singleton Sonarr download client settings resource. */
@Serializable
data class DownloadClientConfigResource(
    val id: Int = 0,
    val downloadClientWorkingFolders: String? = null,
    val enableCompletedDownloadHandling: Boolean = false,
    val autoRedownloadFailed: Boolean = false,
    val autoRedownloadFailedFromInteractiveSearch: Boolean = false
)
