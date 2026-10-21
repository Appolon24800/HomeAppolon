package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr ImportListResource wire resource. Reuses [Field] from the library group.
 * minRefreshInterval is a date-span string and stays unparsed.
 */
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
    val enableAutomaticAdd: Boolean = false,
    val shouldMonitor: ImportListMonitorType? = null,
    val shouldMonitorExisting: Boolean = false,
    val shouldSearch: Boolean = false,
    val rootFolderPath: String? = null,
    val monitorNewItems: NewItemMonitorTypes? = null,
    val qualityProfileId: Int = 0,
    val metadataProfileId: Int = 0,
    val listType: ImportListType? = null,
    val listOrder: Int = 0,
    val minRefreshInterval: String = "",
)
