package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListExclusionResourcePagingResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListExclusionResourcePagingResource(
    val page: Int = 0,
    val pageSize: Int = 0,
    val sortKey: String? = null,
    val sortDirection: SortDirection? = null,
    val totalRecords: Int = 0,
    val records: List<ImportListExclusionResource>? = null,
)
