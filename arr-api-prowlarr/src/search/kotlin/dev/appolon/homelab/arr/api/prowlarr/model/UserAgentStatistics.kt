package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/** Query and grab counters grouped by requesting user agent. */
@Serializable
data class UserAgentStatistics(
    val userAgent: String? = null,
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
)
