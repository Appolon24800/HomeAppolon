package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/** Query and grab counters grouped by requesting host. */
@Serializable
data class HostStatistics(
    val host: String? = null,
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
)
