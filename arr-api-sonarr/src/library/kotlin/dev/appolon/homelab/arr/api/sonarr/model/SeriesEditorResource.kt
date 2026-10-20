package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesEditorResource(
    val seriesIds: List<Int>? = null,
    val monitored: Boolean? = null,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
    val qualityProfileId: Int? = null,
    val language: Language = Language(),
    val seriesType: SeriesTypes = SeriesTypes.STANDARD,
    val seasonFolder: Boolean? = null,
    val rootFolderPath: String? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.ADD,
    val moveFiles: Boolean = false,
    val deleteFiles: Boolean = false,
    val addImportListExclusion: Boolean = false
)
