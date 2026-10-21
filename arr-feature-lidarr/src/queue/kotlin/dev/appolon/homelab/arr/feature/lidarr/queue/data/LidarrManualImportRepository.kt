package dev.appolon.homelab.arr.feature.lidarr.queue.data

import dev.appolon.homelab.arr.api.lidarr.ManualImportApi
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import kotlin.coroutines.CoroutineContext

/** One browsable folder row: a directory path or the parent hop. */
data class ImportFolder(
    val path: String,
    val name: String,
    val isParent: Boolean = false,
)

/** The artist id Lidarr matched to an import candidate; null when unmatched. */
internal fun ManualImportResource.artistIdOrNull(): Int? = artist?.id?.takeIf { it > 0 }

/** The matched artist name, or null when Lidarr could not match one. */
fun ManualImportResource.artistTitleOrNull(): String? =
    artist?.artistName?.takeIf { it.isNotEmpty() }

/** The matched album title, or null when Lidarr could not match one. */
fun ManualImportResource.albumTitleOrNull(): String? = album?.title?.takeIf { it.isNotEmpty() }

/** Track ids of the matched candidate, used by the reprocess payload. */
internal fun ManualImportResource.trackIdsOrEmpty(): List<Int> =
    tracks.orEmpty().map { it.id }

/**
 * Matched artist, album, and release: the minimum the import endpoint needs.
 * Track lists may be empty for additional files, so they are not required.
 */
fun ManualImportResource.isMatched(): Boolean =
    (artist?.id ?: 0) > 0 && (album?.id ?: 0) > 0 && albumReleaseId > 0

private fun JsonObject.stringOf(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

/**
 * Manual import repository over the ManualImport and FileSystem endpoints.
 * Listing is inherently online (the user browses live folders); the import
 * write itself falls back to the [ActionQueue] when offline so a finished
 * selection survives a dropped connection. No function throws. The spec
 * declares no response schema for the filesystem endpoint, so folder
 * listings arrive as raw JSON and are projected here.
 */
class LidarrManualImportRepository(
    private val serverId: ServerId,
    private val manualImportApi: ManualImportApi,
    private val fileSystemCall: suspend (path: String?, includeFiles: Boolean) -> JsonObject,
    private val actionQueue: ActionQueue,
    private val json: kotlinx.serialization.json.Json = ArrJson,
    private val now: () -> Long = System::currentTimeMillis,
    /** Injectable so unit tests run the whole pipeline on virtual time. */
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    /** Folders inside [path]; null [path] lists the server's root view. */
    suspend fun listFolders(path: String?): ArrResult<List<ImportFolder>> =
        withContext(ioContext) {
            val listing = try {
                fileSystemCall(path, false)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: IOException) {
                return@withContext ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            val parent = listing.stringOf("parent")
            val directories = (listing["directories"] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                .orEmpty()
            val folders = directories
                .filter { dir -> dir.stringOf("type") in FOLDER_TYPES }
                .mapNotNull { dir ->
                    dir.stringOf("path")?.let { path ->
                        ImportFolder(path = path, name = dir.stringOf("name") ?: path)
                    }
                }
            ArrResult.ok(
                if (parent == null) {
                    folders
                } else {
                    listOf(ImportFolder(parent, PARENT_LABEL, isParent = true)) + folders
                },
            )
        }

    /** Import candidates of one folder (interactive folder mode). */
    suspend fun listFolderItems(folder: String): ArrResult<List<ManualImportResource>> =
        withContext(ioContext) {
            callManualImport(folder = folder, downloadId = null)
        }

    /** Import candidates of one download, optionally narrowed to its output path. */
    suspend fun listDownloadItems(
        downloadId: String,
        outputPath: String?,
    ): ArrResult<List<ManualImportResource>> = withContext(ioContext) {
        callManualImport(folder = outputPath, downloadId = downloadId)
    }

    /**
     * Imports the selected items. Items without a matched artist, album, and
     * release are the caller's to filter out (the UI refuses the import until
     * every selection has them). Offline, the POST is enqueued.
     */
    suspend fun importSelections(
        selections: List<ManualImportResource>,
    ): ArrResult<QueueWriteOutcome> = withContext(ioContext) {
        val body = selections.map(::toUpdate)
        try {
            manualImportApi.updateManualImport(body)
        } catch (e: HttpException) {
            return@withContext enqueueImport(body, e.toArrError())
        } catch (e: IOException) {
            return@withContext enqueueImport(body, ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext enqueueImport(body, e.arrError())
        }
        ArrResult.ok(QueueWriteOutcome.Synced)
    }

    private suspend fun callManualImport(
        folder: String?,
        downloadId: String?,
    ): ArrResult<List<ManualImportResource>> = try {
        ArrResult.ok(
            manualImportApi.getManualImport(
                folder = folder,
                downloadId = downloadId,
                filterExistingFiles = true,
            ),
        )
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: IOException) {
        ArrResult.err(ArrError.Network(e))
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

    private suspend fun enqueueImport(
        body: List<ManualImportUpdateResource>,
        error: ArrError,
    ): ArrResult<QueueWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.LIDARR,
            method = "POST",
            path = IMPORT_PATH,
            bodyJson = json.encodeToString(
                ListSerializer(ManualImportUpdateResource.serializer()),
                body,
            ),
            description = "Manual import ${body.size} items",
            enqueuedAtMillis = now(),
        )
        actionQueue.enqueue(action)
        return ArrResult.ok(QueueWriteOutcome.Queued(action.actionId))
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    /** Wire shape of one confirmed selection: the picked artist, album, and release ride along. */
    private fun toUpdate(item: ManualImportResource) = ManualImportUpdateResource(
        id = item.id,
        path = item.path,
        artistId = item.artistIdOrNull() ?: 0,
        albumId = item.album?.id ?: 0,
        albumReleaseId = item.albumReleaseId,
        tracks = item.tracks,
        trackIds = item.trackIdsOrEmpty(),
        quality = item.quality,
        releaseGroup = item.releaseGroup,
        indexerFlags = item.indexerFlags,
        downloadId = item.downloadId,
        additionalFile = item.additionalFile,
        replaceExistingFiles = item.replaceExistingFiles,
        disableReleaseSwitching = item.disableReleaseSwitching,
        rejections = item.rejections,
    )

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal const val IMPORT_PATH = "api/v1/manualimport"
        internal const val PARENT_LABEL = ".."

        /** The filesystem endpoint's directory discriminators. */
        private val FOLDER_TYPES = setOf("folder", "drive")
    }
}
