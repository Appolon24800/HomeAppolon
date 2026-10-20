package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr CommandResult. Dates remain unparsed strings. */
@Serializable
enum class CommandResult {
    @SerialName("unknown") UNKNOWN,
    @SerialName("successful") SUCCESSFUL,
    @SerialName("unsuccessful") UNSUCCESSFUL,
    ;

    override fun toString(): String = when (this) {
        UNKNOWN -> "unknown"
        SUCCESSFUL -> "successful"
        UNSUCCESSFUL -> "unsuccessful"
    }
}
