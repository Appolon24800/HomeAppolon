package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One log line from `GET /api/v1/log`. The timestamp stays a wire-format
 * string; the UI parses it with :arr-core ArrTime. `level` has no enum in
 * this spec version, so it stays a plain string.
 */
@Serializable
data class LogResource(
    val id: Int = 0,
    val time: String? = null,
    val exception: String? = null,
    val exceptionType: String? = null,
    val level: String? = null,
    val logger: String? = null,
    val message: String? = null,
    val method: String? = null,
)
