package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MediaManagementConfigResource wire resource for media management settings. */
@Serializable
data class MediaManagementConfigResource(
    val id: Int = 0,
    val autoUnmonitorPreviouslyDownloadedTracks: Boolean = false,
    val recycleBin: String? = null,
    val recycleBinCleanupDays: Int = 0,
    val downloadPropersAndRepacks: ProperDownloadTypes = ProperDownloadTypes.PREFER_AND_UPGRADE,
    val createEmptyArtistFolders: Boolean = false,
    val deleteEmptyFolders: Boolean = false,
    val fileDate: FileDateType = FileDateType.NONE,
    val watchLibraryForChanges: Boolean = false,
    val rescanAfterRefresh: RescanAfterRefreshType = RescanAfterRefreshType.ALWAYS,
    val allowFingerprinting: AllowFingerprinting = AllowFingerprinting.NEVER,
    val setPermissionsLinux: Boolean = false,
    val chmodFolder: String? = null,
    val chownGroup: String? = null,
    val skipFreeSpaceCheckWhenImporting: Boolean = false,
    val minimumFreeSpaceWhenImporting: Int = 0,
    val copyUsingHardlinks: Boolean = false,
    val enableMediaInfo: Boolean = false,
    val useScriptImport: Boolean = false,
    val scriptImportPath: String? = null,
    val importExtraFiles: Boolean = false,
    val extraFileExtensions: String? = null,
)
