package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr AlbumReleaseResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumReleaseResource(
    val id: Int = 0,
    val albumId: Int = 0,
    val foreignReleaseId: String? = null,
    val title: String? = null,
    val status: String? = null,
    val duration: Int = 0,
    val trackCount: Int = 0,
    val media: List<MediumResource>? = null,
    val mediumCount: Int = 0,
    val disambiguation: String? = null,
    val country: List<String>? = null,
    val label: List<String>? = null,
    val format: String? = null,
    val monitored: Boolean = false,
)
