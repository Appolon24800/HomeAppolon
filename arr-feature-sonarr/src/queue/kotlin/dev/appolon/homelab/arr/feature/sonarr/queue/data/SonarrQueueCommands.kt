package dev.appolon.homelab.arr.feature.sonarr.queue.data

import dev.appolon.homelab.arr.core.ArrResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Named Sonarr commands the queue screens dispatch. */
object SonarrQueueCommands {

    /**
     * Sonarr's retry-import command: rescans the download client item and
     * imports it. The typed `CommandResource` model cannot carry command
     * specific fields like `downloadId` or `path` (the OpenAPI spec leaves
     * them undefined), so commands travel as raw JSON through this runner.
     */
    const val RETRY_IMPORT = "DownloadedEpisodesScan"

    /** Searches a replacement for one or more episodes after a removal. */
    const val EPISODE_SEARCH = "EpisodeSearch"
}

/**
 * Runs a named Sonarr command. The composition root implements this over a
 * plain OkHttp POST to `api/v3/command` (with the server's client factory),
 * because the typed [dev.appolon.homelab.arr.api.sonarr.CommandApi] model
 * cannot express command specific payloads.
 */
fun interface QueueCommandRunner {
    suspend fun run(
        name: String,
        downloadId: String?,
        path: String?,
        episodeIds: List<Int>?,
    ): ArrResult<Unit>
}

/**
 * JSON body shared by the online dispatch and the queued replay of a queue
 * command, so both paths send byte-identical requests. Selection ids live at
 * the JSON root, the shape Sonarr's command endpoint requires.
 */
@Serializable
data class QueueCommandBody(
    @SerialName("name") val name: String,
    @SerialName("downloadId") val downloadId: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("episodeIds") val episodeIds: List<Int>? = null,
) {
    fun encode(json: Json): String = json.encodeToString(serializer(), this)

    companion object {
        fun decode(json: Json, body: String): QueueCommandBody? =
            runCatching { json.decodeFromString(serializer(), body) }.getOrNull()
    }
}
