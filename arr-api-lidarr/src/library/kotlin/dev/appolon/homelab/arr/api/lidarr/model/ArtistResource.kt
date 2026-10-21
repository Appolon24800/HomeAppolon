package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr ArtistResource wire resource. Dates remain unparsed strings. */
@Serializable
data class ArtistResource(
    val id: Int = 0,
    val status: ArtistStatusType = ArtistStatusType.CONTINUING,
    val ended: Boolean = false,
    val artistName: String? = null,
    val foreignArtistId: String? = null,
    val mbId: String? = null,
    val tadbId: Int = 0,
    val discogsId: Int = 0,
    val allMusicId: String? = null,
    val overview: String? = null,
    val artistType: String? = null,
    val disambiguation: String? = null,
    val links: List<Links>? = null,
    val nextAlbum: AlbumResource? = null,
    val lastAlbum: AlbumResource? = null,
    val images: List<MediaCover>? = null,
    val members: List<Member>? = null,
    val remotePoster: String? = null,
    val path: String? = null,
    val qualityProfileId: Int = 0,
    val metadataProfileId: Int = 0,
    val monitored: Boolean = false,
    val monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
    val rootFolderPath: String? = null,
    val folder: String? = null,
    val genres: List<String>? = null,
    val cleanName: String? = null,
    val sortName: String? = null,
    val tags: List<Int>? = null,
    val added: String = "",
    val addOptions: AddArtistOptions? = null,
    val ratings: Ratings? = null,
    val statistics: ArtistStatisticsResource? = null,
)
