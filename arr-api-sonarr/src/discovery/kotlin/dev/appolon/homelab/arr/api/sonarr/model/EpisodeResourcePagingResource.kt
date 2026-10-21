package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/**
 * Sonarr EpisodeResourcePagingResource wire contract, the paged envelope for the
 * wanted/missing and wanted/cutoff endpoints. Records reuse the library group's
 * EpisodeResource.
 */
@Serializable
data class EpisodeResourcePagingResource(
    val page: Int = 0,
    val pageSize: Int = 0,
    val sortKey: String? = null,
    val sortDirection: SortDirection = SortDirection.DEFAULT,
    val totalRecords: Int = 0,
    val records: List<EpisodeResource>? = null
)
