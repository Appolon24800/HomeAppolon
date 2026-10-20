package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueResourcePagingResource(
    val page: Int = 0,
    val pageSize: Int = 0,
    val sortKey: String? = null,
    val sortDirection: SortDirection = SortDirection.DEFAULT,
    val totalRecords: Int = 0,
    val records: List<QueueResource>? = null,
)
