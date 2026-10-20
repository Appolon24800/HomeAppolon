package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr DownloadClientBulkResource wire resource for bulk edit and delete. */
@Serializable
data class DownloadClientBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags? = null,
    val enable: Boolean? = null,
    val priority: Int? = null,
    val removeCompletedDownloads: Boolean? = null,
    val removeFailedDownloads: Boolean? = null,
)
