package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr BackupResource. Dates remain unparsed strings. */
@Serializable
data class BackupResource(
    val id: Int = 0,
    val name: String? = null,
    val path: String? = null,
    val type: BackupType? = null,
    val size: Long = 0,
    val time: String = "",
)
