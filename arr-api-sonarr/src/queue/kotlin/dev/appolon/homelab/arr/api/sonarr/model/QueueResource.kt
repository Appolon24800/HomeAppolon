package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Sonarr QueueResource wire contract. Dates remain unparsed strings. */
@Serializable
data class QueueResource(
    val id: Int = 0,
    val seriesId: Int? = null,
    val episodeId: Int? = null,
    val seasonNumber: Int? = null,
    val series: JsonObject = JsonObject(emptyMap()),
    val episode: JsonObject = JsonObject(emptyMap()),
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
    val episodeHasFile: Boolean = false,
    val sizeleft: Double = 0.0,
    val timeleft: String? = null
)
