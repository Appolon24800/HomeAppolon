package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

import dev.appolon.homelab.arr.api.lidarr.FileSystemApi
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
 * One server-side directory row of a filesystem listing. Lidarr documents
 * no response schema for the filesystem endpoints, so the entry is parsed
 * defensively from the raw JSON with nullable fields.
 */
data class LidarrFsEntry(
    val name: String?,
    val path: String?,
    val type: String?,
) {
    /** True when the server flagged the row as a drive/root. */
    val isDrive: Boolean get() = type.equals("DRIVE", ignoreCase = true)
}

/** One listing page: the directories of a path plus the server-computed parent. */
data class FileSystemListing(
    val requestedPath: String?,
    val parent: String?,
    val directories: List<LidarrFsEntry>,
) {
    /** Breadcrumb suggestion when entering a dialog with no path yet. */
    val effectivePath: String? get() = requestedPath ?: directories.firstOrNull()?.path
}

/**
 * Live filesystem listing over the FileSystem endpoints for the path
 * picker dialogs. Nothing is cached: the browser always shows what the
 * server sees right now, and failures surface as LCE retry rows inside
 * the dialog instead of blocking the picker. Lidarr's spec types the
 * response as raw JSON, so the repository owns the parsing and hands the
 * UI typed rows.
 */
class LidarrFileSystemRepository(
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
            val result = fileSystemApi.getFileSystem(path = path, includeFiles = false)
            ArrResult.ok(listingOf(path, result))
        } catch (e: IOException) {
            ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            ArrResult.err(ArrError.Unknown(e))
        }
    }

    private fun listingOf(requested: String?, result: JsonObject): FileSystemListing {
        val directories = (result["directories"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.map { entry ->
                LidarrFsEntry(
                    name = entry.stringOrNull("name"),
                    path = entry.stringOrNull("path"),
                    type = entry.stringOrNull("type"),
                )
            }
            // Drives surface above plain folders so the browser's roots are
            // reachable without scrolling past deep subtrees.
            ?.sortedWith(
                compareBy(
                    { entry ->
                        when (entry.type?.uppercase()) {
                            "DRIVE" -> 0
                            "PARENT", "FOLDER" -> 1
                            else -> 2
                        }
                    },
                    { it.name ?: "" },
                ),
            )
            ?: emptyList()
        return FileSystemListing(
            requestedPath = requested,
            parent = result.stringOrNull("parent"),
            directories = directories,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
