package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr LogResourcePagingResource wire resource for the log table. */
@Serializable
data class LogResourcePagingResource(
    val page: Int = 0,
    val pageSize: Int = 0,
    val sortKey: String? = null,
    val sortDirection: SortDirection = SortDirection.DEFAULT,
    val totalRecords: Int = 0,
    val records: List<LogResource>? = null,
)
