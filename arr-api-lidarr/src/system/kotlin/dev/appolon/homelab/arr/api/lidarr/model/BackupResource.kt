package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr BackupResource wire resource. Time stays an unparsed date-time string. */
@Serializable
data class BackupResource(
    val id: Int = 0,
    val name: String? = null,
    val path: String? = null,
    val type: BackupType? = null,
    val size: Long = 0,
    val time: String = "",
)
