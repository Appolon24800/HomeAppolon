package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr DatabaseType schema. */
@Serializable
enum class DatabaseType(val wireValue: String) {
    @SerialName("sqLite")
    SQLITE("sqLite"),

    @SerialName("postgreSQL")
    POSTGRESQL("postgreSQL");

    override fun toString(): String = wireValue
}
