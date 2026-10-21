package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeResource(
    val id: Int = 0,
    val seriesId: Int = 0,
    val tvdbId: Int = 0,
    val episodeFileId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val airDate: String? = null,
    val airDateUtc: String? = null,
    val lastSearchTime: String? = null,
    val runtime: Int = 0,
    val finaleType: String? = null,
    val overview: String? = null,
    val episodeFile: EpisodeFileResource = EpisodeFileResource(),
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val absoluteEpisodeNumber: Int? = null,
    val sceneAbsoluteEpisodeNumber: Int? = null,
    val sceneEpisodeNumber: Int? = null,
    val sceneSeasonNumber: Int? = null,
    val unverifiedSceneNumbering: Boolean = false,
    val series: SeriesResource = SeriesResource(),
    val images: List<MediaCover>? = null
)
