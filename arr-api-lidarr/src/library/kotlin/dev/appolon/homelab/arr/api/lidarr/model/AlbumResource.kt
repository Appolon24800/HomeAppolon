package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Lidarr AlbumResource wire resource. Dates remain unparsed strings. */
@Serializable
data class AlbumResource(
    val id: Int = 0,
    val title: String? = null,
    val disambiguation: String? = null,
    val overview: String? = null,
    val artistId: Int = 0,
    val foreignAlbumId: String? = null,
    val monitored: Boolean = false,
    val anyReleaseOk: Boolean = false,
    val profileId: Int = 0,
    val duration: Int = 0,
    val albumType: String? = null,
    val secondaryTypes: List<String>? = null,
    val mediumCount: Int = 0,
    val ratings: Ratings? = null,
    val releaseDate: String? = null,
    val releases: List<AlbumReleaseResource>? = null,
    val genres: List<String>? = null,
    val media: List<MediumResource>? = null,
    val artist: ArtistResource? = null,
    val images: List<MediaCover>? = null,
    val links: List<Links>? = null,
    val lastSearchTime: String? = null,
    val statistics: AlbumStatisticsResource? = null,
    val addOptions: AddAlbumOptions? = null,
    val remoteCover: String? = null,
)
