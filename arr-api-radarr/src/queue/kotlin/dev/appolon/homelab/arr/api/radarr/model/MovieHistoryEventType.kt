package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr MovieHistoryEventType. Dates remain unparsed strings. */
@Serializable
enum class MovieHistoryEventType {
    @SerialName("unknown") UNKNOWN,
    @SerialName("grabbed") GRABBED,
    @SerialName("downloadFolderImported") DOWNLOAD_FOLDER_IMPORTED,
    @SerialName("downloadFailed") DOWNLOAD_FAILED,
    @SerialName("movieFileDeleted") MOVIE_FILE_DELETED,
    @SerialName("movieFolderImported") MOVIE_FOLDER_IMPORTED,
    @SerialName("movieFileRenamed") MOVIE_FILE_RENAMED,
    @SerialName("downloadIgnored") DOWNLOAD_IGNORED,
    ;

    override fun toString(): String = when (this) {
        UNKNOWN -> "unknown"
        GRABBED -> "grabbed"
        DOWNLOAD_FOLDER_IMPORTED -> "downloadFolderImported"
        DOWNLOAD_FAILED -> "downloadFailed"
        MOVIE_FILE_DELETED -> "movieFileDeleted"
        MOVIE_FOLDER_IMPORTED -> "movieFolderImported"
        MOVIE_FILE_RENAMED -> "movieFileRenamed"
        DOWNLOAD_IGNORED -> "downloadIgnored"
    }
}
