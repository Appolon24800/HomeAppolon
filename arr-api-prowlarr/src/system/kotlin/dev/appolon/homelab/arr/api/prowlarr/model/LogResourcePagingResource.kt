package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Server-side paging envelope for the log listing, mirroring the spec's
 * LogResourcePagingResource. Shares the [SortDirection] type owned by the
 * search group.
 */
@Serializable
data class LogResourcePagingResource(
    val page: Int = 0,
    val pageSize: Int = 0,
    val sortKey: String? = null,
    val sortDirection: SortDirection = SortDirection.DEFAULT,
    val totalRecords: Int = 0,
    val records: List<LogResource>? = null,
)
