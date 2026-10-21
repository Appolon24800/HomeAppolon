package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Partial update applied to many applications at once. Only [ids] selects
 * rows; the server merges [syncLevel] and [tags] per [applyTags].
 */
@Serializable
data class ApplicationBulkResource(
    val ids: List<Int>? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.REPLACE,
    val syncLevel: ApplicationSyncLevel = ApplicationSyncLevel.DISABLED,
)
