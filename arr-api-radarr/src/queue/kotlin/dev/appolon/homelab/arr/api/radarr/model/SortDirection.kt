package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire contract for Radarr SortDirection. Dates remain unparsed strings. */
@Serializable
enum class SortDirection {
    @SerialName("default") DEFAULT,
    @SerialName("ascending") ASCENDING,
    @SerialName("descending") DESCENDING,
    ;

    override fun toString(): String = when (this) {
        DEFAULT -> "default"
        ASCENDING -> "ascending"
        DESCENDING -> "descending"
    }
}
