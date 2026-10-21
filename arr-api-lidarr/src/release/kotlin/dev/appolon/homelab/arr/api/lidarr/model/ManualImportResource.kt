package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

@Serializable
data class ManualImportResource(
    val id: Int = 0,
    val path: String? = null,
    val name: String? = null,
    val size: Long = 0,
    val artist: ArtistResource? = null,
    val album: AlbumResource? = null,
    val albumReleaseId: Int = 0,
    val tracks: List<TrackResource>? = null,
    val quality: QualityModel? = null,
    val releaseGroup: String? = null,
    val qualityWeight: Int = 0,
    val downloadId: String? = null,
    val indexerFlags: Int = 0,
    val rejections: List<Rejection>? = null,
    val audioTags: ParsedTrackInfo? = null,
    val additionalFile: Boolean = false,
    val replaceExistingFiles: Boolean = false,
    val disableReleaseSwitching: Boolean = false,
)
