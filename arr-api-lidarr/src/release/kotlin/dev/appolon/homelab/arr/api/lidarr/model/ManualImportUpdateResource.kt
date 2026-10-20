package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class ManualImportUpdateResource(
    val id: Int = 0,
    val path: String? = null,
    val name: String? = null,
    val artistId: Int? = null,
    val albumId: Int? = null,
    val albumReleaseId: Int? = null,
    val tracks: List<TrackResource>? = null,
    val trackIds: List<Int>? = null,
    val quality: QualityModel? = null,
    val releaseGroup: String? = null,
    val indexerFlags: Int = 0,
    val downloadId: String? = null,
    val additionalFile: Boolean = false,
    val replaceExistingFiles: Boolean = false,
    val disableReleaseSwitching: Boolean = false,
    val rejections: List<Rejection>? = null,
)
