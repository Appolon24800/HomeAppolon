package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One backup zip the server holds, from `GET /api/v1/system/backup`. The
 * timestamp stays a wire-format string; the UI parses it with :arr-core
 * ArrTime.
 */
@Serializable
data class BackupResource(
    val id: Int = 0,
    val name: String? = null,
    val path: String? = null,
    val type: BackupType? = null,
    val size: Long = 0,
    val time: String? = null,
)
