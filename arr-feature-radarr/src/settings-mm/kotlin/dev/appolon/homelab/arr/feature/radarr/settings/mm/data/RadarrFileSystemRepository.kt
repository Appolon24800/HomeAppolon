package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

import dev.appolon.homelab.arr.api.radarr.FileSystemApi
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemModel
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Live filesystem listing over the FileSystem endpoints for the path
 * picker dialogs. Nothing is cached: the browser always shows what the
 * server sees right now, and failures surface as LCE retry rows inside
 * the dialog instead of blocking the picker.
 */
class RadarrFileSystemRepository(
    private val fileSystemApi: FileSystemApi,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    /**
     * Lists one server-side directory. Folders come back sorted
     * directories-first then alphabetically, and files are excluded:
     * every consumer picks a folder.
     */
    suspend fun list(path: String?): ArrResult<FileSystemListing> = withContext(ioContext) {
        try {
            val result = fileSystemApi.fileSystem(path = path, includeFiles = false)
            ArrResult.ok(listingOf(path, result))
        } catch (e: IOException) {
            ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            ArrResult.err(ArrError.Unknown(e))
        }
    }

    private fun listingOf(requested: String?, result: FileSystemResult): FileSystemListing {
        // Drives surface above plain folders so the browser's roots are
        // reachable without scrolling past deep subtrees.
        val directories = (result.directories ?: emptyList()).sortedWith(
            compareBy(
                { entry ->
                    when (entry.type) {
                        FileSystemEntityType.DRIVE -> 0
                        FileSystemEntityType.PARENT, FileSystemEntityType.FOLDER -> 1
                        else -> 2
                    }
                },
                { it.name ?: "" },
            ),
        )
        return FileSystemListing(
            requestedPath = requested,
            parent = result.parent,
            directories = directories,
        )
    }
}

/** One listing page: the directories of a path plus the server-computed parent. */
data class FileSystemListing(
    val requestedPath: String?,
    val parent: String?,
    val directories: List<FileSystemModel>,
) {
    /** Breadcrumb suggestion when entering a dialog with no path yet. */
    val effectivePath: String? get() = requestedPath ?: directories.firstOrNull()?.path
}
