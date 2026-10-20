package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Partial update applied to every download client in [ids]; null fields are ignored. */
@Serializable
data class DownloadClientBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.ADD,
    val enable: Boolean? = null,
    val priority: Int? = null,
    val removeCompletedDownloads: Boolean? = null,
    val removeFailedDownloads: Boolean? = null
)
