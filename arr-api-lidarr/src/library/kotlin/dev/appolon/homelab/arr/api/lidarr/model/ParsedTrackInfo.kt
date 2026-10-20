package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr ParsedTrackInfo wire resource. Dates remain unparsed strings. */
@Serializable
data class ParsedTrackInfo(
    val title: String? = null,
    val cleanTitle: String? = null,
    val artistTitle: String? = null,
    val albumTitle: String? = null,
    val artistTitleInfo: ArtistTitleInfo? = null,
    val artistMBId: String? = null,
    val albumMBId: String? = null,
    val releaseMBId: String? = null,
    val recordingMBId: String? = null,
    val trackMBId: String? = null,
    val discNumber: Int = 0,
    val discCount: Int = 0,
    val country: IsoCountry? = null,
    val year: Int = 0,
    val label: String? = null,
    val catalogNumber: String? = null,
    val disambiguation: String? = null,
    val duration: String = "",
    val quality: QualityModel? = null,
    val mediaInfo: MediaInfoModel? = null,
    val trackNumbers: List<Int>? = null,
    val releaseGroup: String? = null,
    val releaseHash: String? = null,
)
