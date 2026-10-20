package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values enumerated by the Lidarr OpenAPI schema. */
@Serializable
enum class DatabaseType {
    @SerialName("sqLite") SQ_LITE,
    @SerialName("postgreSQL") POSTGRE_SQL;

    override fun toString(): String = when (this) {
        SQ_LITE -> "sqLite"
        POSTGRE_SQL -> "postgreSQL"
    }
}
