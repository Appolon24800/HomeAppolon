package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr UpdateResource wire resource for one available update package.
 * ReleaseDate and the nullable InstalledOn stay unparsed date-time strings.
 */
@Serializable
data class UpdateResource(
    val id: Int = 0,
    val version: String? = null,
    val branch: String? = null,
    val releaseDate: String = "",
    val fileName: String? = null,
    val url: String? = null,
    val installed: Boolean = false,
    val installedOn: String? = null,
    val installable: Boolean = false,
    val latest: Boolean = false,
    val changes: UpdateChanges = UpdateChanges(),
    val hash: String? = null,
)
