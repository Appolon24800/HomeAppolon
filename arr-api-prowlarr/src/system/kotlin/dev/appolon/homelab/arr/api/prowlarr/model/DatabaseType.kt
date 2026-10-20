package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which database engine the server uses, from the spec's DatabaseType enum.
 * [toString] returns the wire name because the casing is not a valid Kotlin
 * identifier, while bodies use @SerialName.
 */
@Serializable
enum class DatabaseType(private val wireName: String) {
    @SerialName("sqLite") SQLITE("sqLite"),
    @SerialName("postgreSQL") POSTGRESQL("postgreSQL");

    override fun toString(): String = wireName
}
