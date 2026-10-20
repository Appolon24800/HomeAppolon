package dev.appolon.homelab.arr.feature.radarr.queue.data

import dev.appolon.homelab.arr.core.ArrResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Named Radarr commands the queue screens dispatch. */
object RadarrQueueCommands {

    /**
     * Radarr's retry-import command: rescans the download client item and
     * imports it. The typed `CommandResource` model cannot carry command
     * specific fields like `downloadId` or `path` (the OpenAPI spec leaves
     * them undefined), so commands travel as raw JSON through this runner.
     */
    const val RETRY_IMPORT = "DownloadedMoviesScan"

    /** Searches replacements for one or more movies after a removal. */
    const val SEARCH_MOVIES = "MoviesSearch"
}

/**
 * Runs a named Radarr command. The composition root implements this over a
 * plain OkHttp POST to `api/v3/command` (with the server's client factory),
 * because the typed [dev.appolon.homelab.arr.api.radarr.CommandApi] model
 * cannot express command specific payloads.
 */
fun interface QueueCommandRunner {
    suspend fun run(
        name: String,
        downloadId: String?,
        path: String?,
        movieIds: List<Int>?,
    ): ArrResult<Unit>
}

/**
 * JSON body shared by the online dispatch and the queued replay of a queue
 * command, so both paths send byte-identical requests.
 */
@Serializable
data class QueueCommandBody(
    @SerialName("name") val name: String,
    @SerialName("downloadId") val downloadId: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("movieIds") val movieIds: List<Int>? = null,
) {
    fun encode(json: Json): String = json.encodeToString(serializer(), this)

    companion object {
        fun decode(json: Json, body: String): QueueCommandBody? =
            runCatching { json.decodeFromString(serializer(), body) }.getOrNull()
    }
}
