package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Wire values defined by the Sonarr EpisodeHistoryEventType schema. */
@Serializable
enum class EpisodeHistoryEventType(val wireValue: String) {
    @SerialName("unknown")
    UNKNOWN("unknown"),
    @SerialName("grabbed")
    GRABBED("grabbed"),
    @SerialName("seriesFolderImported")
    SERIES_FOLDER_IMPORTED("seriesFolderImported"),
    @SerialName("downloadFolderImported")
    DOWNLOAD_FOLDER_IMPORTED("downloadFolderImported"),
    @SerialName("downloadFailed")
    DOWNLOAD_FAILED("downloadFailed"),
    @SerialName("episodeFileDeleted")
    EPISODE_FILE_DELETED("episodeFileDeleted"),
    @SerialName("episodeFileRenamed")
    EPISODE_FILE_RENAMED("episodeFileRenamed"),
    @SerialName("downloadIgnored")
    DOWNLOAD_IGNORED("downloadIgnored");

    override fun toString(): String = wireValue
}
