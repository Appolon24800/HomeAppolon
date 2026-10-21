package dev.appolon.homelab.arr.feature.radarr.search.data

import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Wire contract for Radarr's Release tag as this feature uses it. The api
 * module does not expose the Release endpoints in this checkout, so the
 * interface and DTO live here, feature-owned, following the api module's
 * conventions (`ArrJson`, spec camelCase, defaults everywhere so queued
 * offline bodies round-trip).
 */
@Serializable
data class RadarrReleaseResource(
    val id: Int = 0,
    val guid: String? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
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
    val imdbId: String? = null,
    val rejections: List<String>? = null,
    val publishDate: String? = null,
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
    val indexerFlags: Int = 0,
    val movieId: Int? = null,
    val downloadClientId: Int? = null,
    val downloadClient: String? = null,
    val shouldOverride: Boolean? = null,
)

/** `GET api/v3/release`: the interactive search results of one movie. */
interface RadarrReleaseApi {
    @GET("api/v3/release")
    suspend fun list(@Query("movieId") movieId: Int): List<RadarrReleaseResource>

    /** Grabs one search result; the body is the release with any overrides applied. */
    @POST("api/v3/release")
    suspend fun grab(@Body body: RadarrReleaseResource): RadarrReleaseResource
}

/** `POST api/v3/release/push`: hands Radarr a release from an outside source. */
interface RadarrReleasePushApi {
    @POST("api/v3/release/push")
    suspend fun push(@Body body: RadarrReleaseResource): List<RadarrReleaseResource>
}

/**
 * One movie's cached search result page. Stored as a single entity row so the
 * per-movie fetchedAt stamp lives with the data; the store's type-wide
 * `lastFetchedAt` cannot express per-movie freshness.
 */
@Serializable
data class RadarrReleasePage(
    val movieId: Int,
    val releases: List<RadarrReleaseResource> = emptyList(),
    val fetchedAtMillis: Long = 0,
)

/** Sort keys the release list offers, per the feature matrix. */
enum class ReleaseSort {
    /** Custom format score, highest first; ties break by release weight. */
    QUALITY_SCORE,

    /** Seeders, most first; usenet rows without seeders sort last. */
    SEEDERS,

    /** Age, youngest first, matching the web UI's age column default. */
    AGE,

    /** Size, largest first. */
    SIZE,
}
