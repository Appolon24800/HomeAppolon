package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One search hit returned by the interactive search endpoints, or pushed to
 * the grab endpoints. Categories reuse the indexers group's
 * [IndexerCategory] and the protocol its [DownloadProtocol];
 * [publishDate] stays a wire-format string that the UI parses with
 * :arr-core ArrTime.
 */
@Serializable
data class ReleaseResource(
    val id: Int = 0,
    val guid: String? = null,
    val age: Int = 0,
    val ageHours: Double = 0.0,
    val ageMinutes: Double = 0.0,
    val size: Long = 0,
    val files: Int? = null,
    val grabs: Int? = null,
    val indexerId: Int = 0,
    val indexer: String? = null,
    val subGroup: String? = null,
    val releaseHash: String? = null,
    val title: String? = null,
    val sortTitle: String? = null,
    val imdbId: Int = 0,
    val tmdbId: Int = 0,
    val tvdbId: Int = 0,
    val tvMazeId: Int = 0,
    val publishDate: String? = null,
    val commentUrl: String? = null,
    val downloadUrl: String? = null,
    val infoUrl: String? = null,
    val posterUrl: String? = null,
    val indexerFlags: List<String>? = null,
    val categories: List<IndexerCategory>? = null,
    val magnetUrl: String? = null,
    val infoHash: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val fileName: String? = null,
    val downloadClientId: Int? = null,
)
