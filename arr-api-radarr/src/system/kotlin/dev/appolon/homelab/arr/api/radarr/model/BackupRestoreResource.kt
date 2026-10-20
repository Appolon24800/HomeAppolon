package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr BackupRestoreResource. Dates remain unparsed strings. */
@Serializable
data class BackupRestoreResource(
    val restartRequired: Boolean = false,
)

/** Wire contract for Radarr BackupRestoreErrorResource. Dates remain unparsed strings. */
@Serializable
data class BackupRestoreErrorResource(
    val error: String? = null,
)
