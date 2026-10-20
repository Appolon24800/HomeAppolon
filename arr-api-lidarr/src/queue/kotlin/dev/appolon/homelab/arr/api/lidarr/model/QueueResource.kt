package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Queue wire resource. Dates and durations remain strings for ArrTime consumers. */
@Serializable
data class QueueResource(
    val id: Int = 0,
    val artistId: Int? = null,
    val albumId: Int? = null,
    val artist: ArtistResource? = null,
    val album: AlbumResource? = null,
    val quality: QualityModel? = null,
    val customFormats: List<CustomFormatResource>? = null,
    val customFormatScore: Int = 0,
    val size: Double = 0.0,
    val title: String? = null,
    val sizeleft: Double = 0.0,
    val timeleft: String? = null,
    val estimatedCompletionTime: String? = null,
    val added: String? = null,
    val status: String? = null,
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
    val trackFileCount: Int = 0,
    val trackHasFileCount: Int = 0,
    val downloadForced: Boolean = false,
)
