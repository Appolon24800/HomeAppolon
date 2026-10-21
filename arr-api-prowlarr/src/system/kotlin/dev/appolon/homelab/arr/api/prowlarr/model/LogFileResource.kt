package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Metadata about one log file, from `GET /api/v1/log/file` and
 * `GET /api/v1/log/file/update`. The timestamp stays a wire-format string;
 * the UI parses it with :arr-core ArrTime.
 */
@Serializable
data class LogFileResource(
    val id: Int = 0,
    val filename: String? = null,
    val lastWriteTime: String? = null,
    val contentsUrl: String? = null,
    val downloadUrl: String? = null,
)
