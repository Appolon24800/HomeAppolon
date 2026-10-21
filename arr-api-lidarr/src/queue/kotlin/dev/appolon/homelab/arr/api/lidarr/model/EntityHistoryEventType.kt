package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EntityHistoryEventType(private val wireName: String) {
    @SerialName("unknown") UNKNOWN("unknown"),
    @SerialName("grabbed") GRABBED("grabbed"),
    @SerialName("artistFolderImported") ARTIST_FOLDER_IMPORTED("artistFolderImported"),
    @SerialName("trackFileImported") TRACK_FILE_IMPORTED("trackFileImported"),
    @SerialName("downloadFailed") DOWNLOAD_FAILED("downloadFailed"),
    @SerialName("trackFileDeleted") TRACK_FILE_DELETED("trackFileDeleted"),
    @SerialName("trackFileRenamed") TRACK_FILE_RENAMED("trackFileRenamed"),
    @SerialName("albumImportIncomplete") ALBUM_IMPORT_INCOMPLETE("albumImportIncomplete"),
    @SerialName("downloadImported") DOWNLOAD_IMPORTED("downloadImported"),
    @SerialName("trackFileRetagged") TRACK_FILE_RETAGGED("trackFileRetagged"),
    @SerialName("downloadIgnored") DOWNLOAD_IGNORED("downloadIgnored");

    override fun toString(): String = wireName
}
