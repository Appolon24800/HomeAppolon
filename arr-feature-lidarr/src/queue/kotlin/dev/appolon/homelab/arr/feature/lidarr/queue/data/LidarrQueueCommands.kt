package dev.appolon.homelab.arr.feature.lidarr.queue.data

import dev.appolon.homelab.arr.core.ArrResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Named Lidarr commands the queue screens dispatch. */
object LidarrQueueCommands {

    /**
     * Lidarr's retry-import command: rescans the download client item and
     * imports it. The typed `CommandResource` model cannot carry command
     * specific fields like `downloadId` or `path` (the OpenAPI spec leaves
     * them undefined), so commands travel as raw JSON through this runner.
     */
    const val RETRY_IMPORT = "DownloadedAlbumsScan"

    /** Searches a replacement for one or more albums after a removal. */
    const val ALBUM_SEARCH = "AlbumSearch"
}

/**
 * Runs a named Lidarr command. The composition root implements this over a
 * plain OkHttp POST to `api/v1/command` (with the server's client factory),
 * because the typed [dev.appolon.homelab.arr.api.lidarr.CommandApi] model
 * cannot express command specific payloads.
 */
fun interface QueueCommandRunner {
    suspend fun run(
        name: String,
        downloadId: String?,
        path: String?,
        albumIds: List<Int>?,
    ): ArrResult<Unit>
}

/**
 * JSON body shared by the online dispatch and the queued replay of a queue
 * command, so both paths send byte-identical requests. Selection ids live at
 * the JSON root, the shape Lidarr's command endpoint requires.
 */
@Serializable
data class QueueCommandBody(
    @SerialName("name") val name: String,
    @SerialName("downloadId") val downloadId: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("albumIds") val albumIds: List<Int>? = null,
) {
    fun encode(json: Json): String = json.encodeToString(serializer(), this)

    companion object {
        fun decode(json: Json, body: String): QueueCommandBody? =
            runCatching { json.decodeFromString(serializer(), body) }.getOrNull()
    }
}
