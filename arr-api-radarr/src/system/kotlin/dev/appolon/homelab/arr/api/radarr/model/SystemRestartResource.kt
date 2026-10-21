package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr SystemRestartResource. Dates remain unparsed strings. */
@Serializable
data class SystemRestartResource(
    val restarting: Boolean = false,
)

/** Wire contract for Radarr SystemShutdownResource. Dates remain unparsed strings. */
@Serializable
data class SystemShutdownResource(
    val shuttingDown: Boolean = false,
)
