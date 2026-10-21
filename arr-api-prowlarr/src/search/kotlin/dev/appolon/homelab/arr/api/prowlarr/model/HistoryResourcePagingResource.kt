package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sort order for paged listings, from the spec's SortDirection enum. The
 * log endpoints in the system group share this type. [toString] returns the
 * wire name because Retrofit serializes @Query values through the enum's
 * toString fallback, while bodies use @SerialName.
 */
@Serializable
enum class SortDirection(private val wireName: String) {
    @SerialName("default") DEFAULT("default"),
    @SerialName("ascending") ASCENDING("ascending"),
    @SerialName("descending") DESCENDING("descending");

    override fun toString(): String = wireName
}

/**
 * Server-side paging envelope for the history listing, mirroring the spec's
 * HistoryResourcePagingResource.
 */
@Serializable
data class HistoryResourcePagingResource(
    val page: Int = 0,
    val pageSize: Int = 0,
    val sortKey: String? = null,
    val sortDirection: SortDirection = SortDirection.DEFAULT,
    val totalRecords: Int = 0,
    val records: List<HistoryResource>? = null,
)
