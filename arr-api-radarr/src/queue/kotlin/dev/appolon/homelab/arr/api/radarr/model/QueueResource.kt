package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr QueueResource. Dates remain unparsed strings. */
@Serializable
data class QueueResource(
    val id: Int = 0,
    val movieId: Int? = null,
    val movie: MovieResource = MovieResource(),
    val languages: List<Language>? = null,
    val quality: QualityModel = QualityModel(),
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val size: Double = 0.0,
    val title: String? = null,
    val estimatedCompletionTime: String? = null,
    val added: String? = null,
    val status: QueueStatus = QueueStatus.UNKNOWN,
    val trackedDownloadStatus: TrackedDownloadStatus = TrackedDownloadStatus.OK,
    val trackedDownloadState: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
    val statusMessages: List<TrackedDownloadStatusMessage>? = null,
    val errorMessage: String? = null,
    val downloadId: String? = null,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val downloadClient: String? = null,
    val downloadClientHasPostImportCategory: Boolean = false,
    val indexer: String? = null,
    val outputPath: String? = null,
    val sizeleft: Double = 0.0,
    val timeleft: String? = null,
)
