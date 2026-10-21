package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr DownloadClientBulkResource.
 * Dates remain unparsed strings. */
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
