package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr MediaManagementConfigResource. Dates remain unparsed strings. */
@Serializable
data class MediaManagementConfigResource(
    val id: Int = 0,
    val autoUnmonitorPreviouslyDownloadedMovies: Boolean = false,
    val recycleBin: String? = null,
    val recycleBinCleanupDays: Int = 0,
    val downloadPropersAndRepacks: ProperDownloadTypes? = null,
    val createEmptyMovieFolders: Boolean = false,
    val deleteEmptyFolders: Boolean = false,
    val fileDate: FileDateType? = null,
    val rescanAfterRefresh: RescanAfterRefreshType? = null,
    val autoRenameFolders: Boolean = false,
    val pathsDefaultStatic: Boolean = false,
    val setPermissionsLinux: Boolean = false,
    val chmodFolder: String? = null,
    val chownGroup: String? = null,
    val skipFreeSpaceCheckWhenImporting: Boolean = false,
    val minimumFreeSpaceWhenImporting: Int = 0,
    val copyUsingHardlinks: Boolean = false,
    val useScriptImport: Boolean = false,
    val scriptImportPath: String? = null,
    val importExtraFiles: Boolean = false,
    val extraFileExtensions: String? = null,
    val enableMediaInfo: Boolean = false,
)
