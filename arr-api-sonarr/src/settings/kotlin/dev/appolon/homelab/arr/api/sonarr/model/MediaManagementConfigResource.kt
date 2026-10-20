package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr MediaManagementConfigResource wire contract. */
@Serializable
data class MediaManagementConfigResource(
    val id: Int = 0,
    val autoUnmonitorPreviouslyDownloadedEpisodes: Boolean = false,
    val recycleBin: String? = null,
    val recycleBinCleanupDays: Int = 0,
    val downloadPropersAndRepacks: ProperDownloadTypes? = null,
    val createEmptySeriesFolders: Boolean = false,
    val deleteEmptyFolders: Boolean = false,
    val fileDate: FileDateType? = null,
    val rescanAfterRefresh: RescanAfterRefreshType? = null,
    val setPermissionsLinux: Boolean = false,
    val chmodFolder: String? = null,
    val chownGroup: String? = null,
    val episodeTitleRequired: EpisodeTitleRequiredType? = null,
    val skipFreeSpaceCheckWhenGrabbing: Boolean = false,
    val skipFreeSpaceCheckWhenImporting: Boolean = false,
    val minimumFreeSpaceWhenImporting: Int = 0,
    val copyUsingHardlinks: Boolean = false,
    val useScriptImport: Boolean = false,
    val scriptImportPath: String? = null,
    val importExtraFiles: Boolean = false,
    val extraFileExtensions: String? = null,
    val enableMediaInfo: Boolean = false,
    val userRejectedExtensions: String? = null,
    val seasonPackUpgrade: SeasonPackUpgradeType? = null,
    val seasonPackUpgradeThreshold: Double? = null
)
