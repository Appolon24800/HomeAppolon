package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr RootFolderResource wire resource. FreeSpace and TotalSpace are
 * nullable int64 on the wire; defaults are applied server side.
 */
@Serializable
data class RootFolderResource(
    val id: Int = 0,
    val name: String? = null,
    val path: String? = null,
    val defaultMetadataProfileId: Int = 0,
    val defaultQualityProfileId: Int = 0,
    val defaultMonitorOption: MonitorTypes = MonitorTypes.ALL,
    val defaultNewItemMonitorOption: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
    val defaultTags: Set<Int>? = null,
    val accessible: Boolean = false,
    val freeSpace: Long? = null,
    val totalSpace: Long? = null,
)
