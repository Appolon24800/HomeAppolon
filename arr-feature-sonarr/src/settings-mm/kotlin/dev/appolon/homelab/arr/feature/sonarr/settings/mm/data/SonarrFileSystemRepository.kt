package dev.appolon.homelab.arr.feature.sonarr.settings.mm.data

import dev.appolon.homelab.arr.api.sonarr.FileSystemApi
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.CoroutineContext

/**
 * One directory row from the server's filesystem listing. The Sonarr spec
 * declares no response schema, so `type` stays the raw wire string and
 * only the `drive` spelling is special-cased for icons.
 */
data class FileSystemEntry(
    val name: String? = null,
    val path: String? = null,
    val type: String? = null,
) {
    val isDrive: Boolean get() = type?.equals("drive", ignoreCase = true) == true
}

/** One listing page: the directories of a path plus the server-computed parent. */
data class FileSystemListing(
    val requestedPath: String?,
    val parent: String?,
    val directories: List<FileSystemEntry>,
) {
    /** Breadcrumb suggestion when entering a dialog with no path yet. */
    val effectivePath: String? get() = requestedPath ?: directories.firstOrNull()?.path
}

/**
 * Live filesystem listing over the FileSystem endpoint for the path
 * picker dialogs. Nothing is cached: the browser always shows what the
 * server sees right now, and failures surface as LCE retry rows inside
 * the dialog instead of blocking the picker.
 */
class SonarrFileSystemRepository(
    private val fileSystemApi: FileSystemApi,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    /**
     * Lists one server-side directory. Folders come back sorted drives
     * first then alphabetically, and files are excluded: every consumer
     * picks a folder.
     */
    suspend fun list(path: String?): ArrResult<FileSystemListing> = withContext(ioContext) {
        try {
            val result = fileSystemApi.list(path = path, includeFiles = false)
            ArrResult.ok(listingOf(path, result))
        } catch (e: IOException) {
            ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            ArrResult.err(e.arrErrorOrUnknown())
        }
    }

    private fun listingOf(requested: String?, result: JsonObject): FileSystemListing {
        val directories = (result["directories"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .map { entry ->
                FileSystemEntry(
                    name = entry.stringOrNull("name"),
                    path = entry.stringOrNull("path"),
                    type = entry.stringOrNull("type"),
                )
            }
            .sortedWith(compareByDescending<FileSystemEntry> { it.isDrive }.thenBy { it.name ?: "" })
        return FileSystemListing(
            requestedPath = requested,
            parent = result.stringOrNull("parent"),
            directories = directories,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun Throwable.arrErrorOrUnknown(): ArrError = when (this) {
        is retrofit2.HttpException -> when (code()) {
            401, 403 -> ArrError.Auth(code())
            else -> ArrError.Http(code(), message())
        }

        else -> ArrError.Unknown(this)
    }
}
