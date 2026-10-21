package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/**
 * Sonarr ImportListResource wire contract. Dates stay unparsed strings; `presets` is
 * recursive schema presets served by `GET importlist/schema`.
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
    val tags: List<Int>? = null,
    val presets: List<ImportListResource>? = null,
    val enableAutomaticAdd: Boolean = false,
    val searchForMissingEpisodes: Boolean = false,
    val shouldMonitor: MonitorTypes = MonitorTypes.UNKNOWN,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
    val rootFolderPath: String? = null,
    val qualityProfileId: Int = 0,
    val seriesType: SeriesTypes = SeriesTypes.STANDARD,
    val language: Language = Language(),
    val seasonFolder: Boolean = false,
    val listType: ImportListType = ImportListType.PROGRAM,
    val listOrder: Int = 0,
    val minRefreshInterval: String? = null,
    val tagExisting: TagExistingType = TagExistingType.NONE
)
