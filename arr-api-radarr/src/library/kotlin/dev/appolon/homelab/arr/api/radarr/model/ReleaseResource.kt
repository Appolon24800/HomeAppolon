package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ReleaseResource(
    val id: Int = 0,
    val guid: String? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val history: ReleaseHistoryResource = ReleaseHistoryResource(),
    val qualityWeight: Int = 0,
    val age: Int = 0,
    val ageHours: Double = 0.0,
    val ageMinutes: Double = 0.0,
    val size: Long = 0L,
    val indexerId: Int = 0,
    val indexer: String? = null,
    val releaseGroup: String? = null,
    val subGroup: String? = null,
    val releaseHash: String? = null,
    val title: String? = null,
    val sceneSource: Boolean = false,
    val movieTitles: List<String>? = null,
    val languages: List<Language>? = null,
    val mappedMovieId: Int? = null,
    val approved: Boolean = false,
    val temporarilyRejected: Boolean = false,
    val rejected: Boolean = false,
    val tmdbId: Int = 0,
    val imdbId: Int = 0,
    val rejections: List<String>? = null,
    val publishDate: String = "1970-01-01T00:00:00Z",
    val commentUrl: String? = null,
    val downloadUrl: String? = null,
    val infoUrl: String? = null,
    val movieRequested: Boolean = false,
    val downloadAllowed: Boolean = false,
    val releaseWeight: Int = 0,
    val edition: String? = null,
    val magnetUrl: String? = null,
    val infoHash: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val indexerFlags: JsonElement? = null,
    val movieId: Int? = null,
    val downloadClientId: Int? = null,
    val downloadClient: String? = null,
    val shouldOverride: Boolean? = null,
)

@Serializable
data class ReleaseHistoryResource(
    val grabbed: String? = null,
    val failed: String? = null,
)
