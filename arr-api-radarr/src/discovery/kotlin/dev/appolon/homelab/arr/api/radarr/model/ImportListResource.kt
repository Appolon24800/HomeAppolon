package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportListResource.
 * Dates remain unparsed strings. */
@Serializable
data class ImportListResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage? = null,
    val tags: Set<Int>? = null,
    val presets: List<ImportListResource>? = null,
    val enabled: Boolean = false,
    val enableAuto: Boolean = false,
    val monitor: MonitorTypes? = null,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int = 0,
    val searchOnAdd: Boolean = false,
    val minimumAvailability: MovieStatusType? = null,
    val listType: ImportListType? = null,
    val listOrder: Int = 0,
    val minRefreshInterval: String = "",
)
