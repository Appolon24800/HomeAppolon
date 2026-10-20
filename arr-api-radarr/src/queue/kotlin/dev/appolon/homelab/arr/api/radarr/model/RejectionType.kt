package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr RejectionType. Dates remain unparsed strings. */
@Serializable
enum class RejectionType {
    @SerialName("permanent") PERMANENT,
    @SerialName("temporary") TEMPORARY,
    ;

    override fun toString(): String = when (this) {
        PERMANENT -> "permanent"
        TEMPORARY -> "temporary"
    }
}
