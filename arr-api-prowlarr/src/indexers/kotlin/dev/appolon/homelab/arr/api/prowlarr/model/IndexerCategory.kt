package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Newznab-style category tree returned by the default-categories endpoint and
 * embedded in indexer capabilities. [subCategories] is server-computed
 * (readOnly in the spec), so callers must not send it back on writes.
 */
@Serializable
data class IndexerCategory(
    val id: Int = 0,
    val name: String? = null,
    val description: String? = null,
    val subCategories: List<IndexerCategory>? = null,
)
