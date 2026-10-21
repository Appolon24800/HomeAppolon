package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr DatabaseType wire enum, constants in spec order. */
@Serializable
enum class DatabaseType {
    @SerialName("sqLite") SQLITE,
    @SerialName("postgreSQL") POSTGRESQL,
    ;

    override fun toString(): String = when (this) {
        SQLITE -> "sqLite"
        POSTGRESQL -> "postgreSQL"
    }
}
