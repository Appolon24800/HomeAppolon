package dev.appolon.homelab.arr.feature.radarr.queue.data

import dev.appolon.homelab.arr.api.radarr.ManualImportApi
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
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
import retrofit2.HttpException
import retrofit2.Response
import kotlin.coroutines.CoroutineContext

/** One browsable folder row: a directory path or the parent hop. */
data class ImportFolder(
    val path: String,
    val name: String,
    val isParent: Boolean = false,
)

/**
 * Manual import repository over the ManualImport and FileSystem endpoints.
 * Listing is inherently online (the user browses live folders); the import
 * write itself falls back to the [ActionQueue] when offline so a finished
 * selection survives a dropped connection. No function throws.
 */
class RadarrManualImportRepository(
    private val serverId: ServerId,
    private val manualImportApi: ManualImportApi,
    private val fileSystemCall: suspend (path: String?, includeFiles: Boolean) -> FileSystemResult,
    private val actionQueue: ActionQueue,
    private val json: kotlinx.serialization.json.Json = ArrJson,
    private val now: () -> Long = System::currentTimeMillis,
    /** Injectable so unit tests run the whole pipeline on virtual time. */
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    /** Folders inside [path]; null [path] lists the server's root view. */
    suspend fun listFolders(path: String?): ArrResult<List<ImportFolder>> =
        withContext(ioContext) {
            val result = try {
                fileSystemCall(path, false)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: IOException) {
                return@withContext ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            val parent = result.parent?.takeIf { it.isNotEmpty() }
            val folders = (result.directories.orEmpty())
                .filter { it.type == FileSystemEntityType.FOLDER || it.type == FileSystemEntityType.DRIVE }
                .mapNotNull { model -> model.path?.let { ImportFolder(path = it, name = model.name ?: it) } }
            ArrResult.ok(
                if (parent == null) folders else listOf(ImportFolder(parent, PARENT_LABEL, isParent = true)) + folders,
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
     * Imports the selected items. Items without a picked movie are the
     * caller's to filter out (the UI refuses the import until every selection
     * has one). Offline, the POST is enqueued.
     */
    suspend fun importSelections(
        selections: List<ManualImportResource>,
    ): ArrResult<QueueWriteOutcome> = withContext(ioContext) {
        val body = selections.map(::toReprocess)
        val response = try {
            manualImportApi.reprocessManualImport(body)
        } catch (e: HttpException) {
            return@withContext enqueueImport(body, e.toArrError())
        } catch (e: IOException) {
            return@withContext enqueueImport(body, ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext enqueueImport(body, e.arrError())
        }
        if (response.isSuccessful) {
            ArrResult.ok(QueueWriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    private suspend fun callManualImport(
        folder: String?,
        downloadId: String?,
    ): ArrResult<List<ManualImportResource>> = try {
        ArrResult.ok(
            manualImportApi.manualImport(
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
        body: List<ManualImportReprocessResource>,
        error: ArrError,
    ): ArrResult<QueueWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:POST:$IMPORT_PATH:$body".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.RADARR,
            method = "POST",
            path = IMPORT_PATH,
            bodyJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ManualImportReprocessResource.serializer()),
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

    /** Wire shape of one confirmed selection: the picked movie rides along. */
    private fun toReprocess(item: ManualImportResource) = ManualImportReprocessResource(
        id = item.id,
        path = item.path,
        movieId = item.movie.id,
        movie = item.movie,
        quality = item.quality,
        languages = item.languages,
        releaseGroup = item.releaseGroup,
        downloadId = item.downloadId,
        customFormats = item.customFormats,
        customFormatScore = item.customFormatScore,
        indexerFlags = item.indexerFlags,
        rejections = item.rejections,
    )

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> {
            val snippet = runCatching { errorBody()?.string() }.getOrNull()
                ?.trim()
                ?.take(200)
                ?.takeIf { it.isNotEmpty() }
            ArrError.Http(code(), snippet)
        }
    }

    companion object {
        internal const val IMPORT_PATH = "api/v3/manualimport"
        internal const val PARENT_LABEL = ".."
    }
}
