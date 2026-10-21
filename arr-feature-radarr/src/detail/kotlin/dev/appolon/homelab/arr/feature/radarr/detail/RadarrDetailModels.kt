package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.CreditType
import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ArrTime
import java.time.Instant

/**
 * UI-facing snapshot of one movie for the detail screen. Image URLs are
 * already resolved against the server's base URL (including any path prefix)
 * so Compose can load them without further context.
 */
data class MovieDetailUi(
    val id: Int,
    val title: String,
    val year: Int,
    val overview: String?,
    val runtimeMinutes: Int,
    val certification: String?,
    val studio: String?,
    val status: MovieStatusType?,
    val monitored: Boolean,
    val qualityProfileId: Int,
    val minimumAvailability: MovieStatusType?,
    val path: String?,
    val folder: String?,
    val sizeOnDisk: Long,
    val hasFile: Boolean,
    val inCinemas: Instant?,
    val physicalRelease: Instant?,
    val digitalRelease: Instant?,
    val added: Instant?,
    val genres: List<String>,
    val tags: Set<Int>,
    val imdbRating: Double?,
    val tmdbRating: Double?,
    val website: String?,
    val imdbId: String?,
    val tmdbId: Int,
    val collectionTitle: String?,
    val alternativeTitles: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
)

/** One cast or crew credit with a resolved headshot URL. */
data class CreditUi(
    val id: Int,
    val name: String,
    val character: String?,
    val job: String?,
    val department: String?,
    val isCast: Boolean,
    val headshotUrl: String?,
)

/** One physical file with its quality, parsed lazily for display. */
data class MovieFileUi(
    val id: Int,
    val relativePath: String,
    val sizeBytes: Long,
    val qualityName: String?,
    val qualityCutoffNotMet: Boolean,
    val languages: String,
    val edition: String?,
    val releaseGroup: String?,
    val dateAdded: Instant?,
    val customFormats: String,
    val customFormatScore: Int?,
    val mediaSummary: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val resolution: String?,
    val videoDynamicRange: String?,
    val audioChannels: Double,
)

/** One history event for the movie. */
data class HistoryUi(
    val id: Int,
    val eventType: String,
    val sourceTitle: String,
    val date: Instant?,
    val qualityName: String?,
    val languages: String,
    val message: String?,
)

/** One sidecar file (subtitle, metadata) next to a movie file. */
data class ExtraFileUi(
    val id: Int,
    val relativePath: String,
    val extension: String,
    val typeName: String?,
    val languageTags: String,
)

/** Editable slice of a movie, backing the edit sheet form. */
data class EditableMovieUi(
    val monitored: Boolean = false,
    val qualityProfileId: Int = 0,
    val minimumAvailability: MovieStatusType? = null,
    val path: String = "",
    val tags: Set<Int> = emptySet(),
) {
    companion object {
        fun from(movie: MovieResource): EditableMovieUi = EditableMovieUi(
            monitored = movie.monitored,
            qualityProfileId = movie.qualityProfileId,
            minimumAvailability = movie.minimumAvailability,
            path = movie.path.orEmpty(),
            tags = movie.tags.orEmpty(),
        )
    }
}

/** Dropdown options for the edit sheet. */
data class EditOptionsUi(
    val qualityProfiles: List<NamedIdUi> = emptyList(),
    val tags: List<NamedIdUi> = emptyList(),
) {
    fun qualityProfileName(id: Int): String? =
        qualityProfiles.firstOrNull { it.id == id }?.name
}

/** Generic id + label pair for pickers. */
data class NamedIdUi(val id: Int, val name: String)

/** The Ok value, or [default] when the load failed; sections degrade silently. */
internal fun <T> ArrResult<T>.valueOrDefault(default: T): T = when (this) {
    is ArrResult.Ok -> value
    is ArrResult.Err -> default
}

/** Maps a stored [MovieResource] onto [MovieDetailUi], resolving image URLs. */
fun MovieResource.toMovieDetailUi(baseUrl: String): MovieDetailUi = MovieDetailUi(
    id = id,
    title = title.orEmpty().ifBlank { sortTitle.orEmpty() },
    year = year,
    overview = overview,
    runtimeMinutes = runtime,
    certification = certification,
    studio = studio,
    status = status,
    monitored = monitored,
    qualityProfileId = qualityProfileId,
    minimumAvailability = minimumAvailability,
    path = path ?: folderName,
    folder = folder ?: folderName,
    sizeOnDisk = sizeOnDisk ?: statistics?.sizeOnDisk ?: 0L,
    hasFile = hasFile ?: (movieFileId != 0),
    inCinemas = ArrTime.parse(inCinemas),
    physicalRelease = ArrTime.parse(physicalRelease),
    digitalRelease = ArrTime.parse(digitalRelease),
    added = ArrTime.parse(added),
    genres = genres.orEmpty(),
    tags = tags.orEmpty(),
    imdbRating = ratings?.imdb?.value?.takeIf { it > 0.0 },
    tmdbRating = ratings?.tmdb?.value?.takeIf { it > 0.0 },
    website = website,
    imdbId = imdbId,
    tmdbId = tmdbId,
    collectionTitle = collection?.title,
    alternativeTitles = alternateTitles.orEmpty().mapNotNull { it.title },
    posterUrl = RadarrDetailLogic.posterUrl(baseUrl, this),
    backdropUrl = RadarrDetailLogic.backdropUrl(baseUrl, this),
)

/** Maps a credit onto its UI shape with a resolved headshot URL. */
fun CreditResource.toCreditUi(baseUrl: String): CreditUi = CreditUi(
    id = id,
    name = personName.orEmpty(),
    character = character,
    job = job,
    department = department,
    isCast = type == CreditType.CAST,
    headshotUrl = RadarrDetailLogic.headshotUrl(baseUrl, this),
)

/** Maps a movie file onto its UI shape. */
fun MovieFileResource.toMovieFileUi(): MovieFileUi = MovieFileUi(
    id = id,
    relativePath = relativePath ?: path?.substringAfterLast('/') ?: "",
    sizeBytes = size,
    qualityName = quality?.quality?.name,
    qualityCutoffNotMet = qualityCutoffNotMet,
    languages = languages.orEmpty().mapNotNull { it.name }.joinToString(", "),
    edition = edition,
    releaseGroup = releaseGroup,
    dateAdded = ArrTime.parse(dateAdded),
    customFormats = customFormats.orEmpty().mapNotNull { it.name }.joinToString(", "),
    customFormatScore = customFormatScore,
    mediaSummary = mediaInfo?.let(::mediaSummary),
    videoCodec = mediaInfo?.videoCodec,
    audioCodec = mediaInfo?.audioCodec,
    resolution = mediaInfo?.resolution,
    videoDynamicRange = mediaInfo?.videoDynamicRange?.takeIf { it.isNotBlank() },
    audioChannels = mediaInfo?.audioChannels ?: 0.0,
)

private fun mediaSummary(info: dev.appolon.homelab.arr.api.radarr.model.MediaInfoResource): String =
    listOfNotNull(
        info.resolution?.takeIf { it.isNotBlank() },
        info.videoCodec?.takeIf { it.isNotBlank() },
        info.audioCodec?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** Maps a history event onto its UI shape. */
fun HistoryResource.toHistoryUi(): HistoryUi = HistoryUi(
    id = id,
    eventType = eventType.name,
    sourceTitle = sourceTitle.orEmpty(),
    date = ArrTime.parse(date),
    qualityName = quality.quality?.name,
    languages = languages.orEmpty().mapNotNull { it.name }.joinToString(", "),
    message = data?.get("message"),
)

/** Maps an extra file onto its UI shape. */
fun ExtraFileResource.toExtraFileUi(): ExtraFileUi = ExtraFileUi(
    id = id,
    relativePath = relativePath.orEmpty(),
    extension = extension.orEmpty(),
    typeName = type?.name,
    languageTags = languageTags.orEmpty().joinToString(", "),
)
