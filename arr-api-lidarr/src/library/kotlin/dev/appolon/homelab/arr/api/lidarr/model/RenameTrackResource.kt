package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr RenameTrackResource wire resource. Dates remain unparsed strings. */
@Serializable
data class RenameTrackResource(
    val id: Int = 0,
    val artistId: Int = 0,
    val albumId: Int = 0,
    val trackNumbers: List<Int>? = null,
    val trackFileId: Int = 0,
    val existingPath: String? = null,
    val newPath: String? = null,
)
