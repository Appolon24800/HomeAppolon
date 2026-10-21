package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MetadataProfileResource wire resource. Dates remain unparsed strings. */
@Serializable
data class MetadataProfileResource(
    val id: Int = 0,
    val name: String? = null,
    val primaryAlbumTypes: List<ProfilePrimaryAlbumTypeItemResource>? = null,
    val secondaryAlbumTypes: List<ProfileSecondaryAlbumTypeItemResource>? = null,
    val releaseStatuses: List<ProfileReleaseStatusItemResource>? = null,
)
