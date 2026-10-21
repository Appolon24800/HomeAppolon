package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MetadataProviderConfigResource wire resource. Dates remain unparsed strings. */
@Serializable
data class MetadataProviderConfigResource(
    val id: Int = 0,
    val metadataSource: String? = null,
    val writeAudioTags: WriteAudioTagsType? = null,
    val scrubAudioTags: Boolean = false,
    val embedCoverArt: Boolean = false,
)
