package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr ArtistEditorResource wire resource. Dates remain unparsed strings. */
@Serializable
data class ArtistEditorResource(
    val artistIds: List<Int>? = null,
    val monitored: Boolean? = null,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
    val qualityProfileId: Int? = null,
    val metadataProfileId: Int? = null,
    val rootFolderPath: String? = null,
    val tags: List<Int>? = null,
    val applyTags: ApplyTags = ApplyTags.ADD,
    val moveFiles: Boolean = false,
    val deleteFiles: Boolean = false,
    val addImportListExclusion: Boolean = false,
)
