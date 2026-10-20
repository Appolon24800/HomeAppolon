package dev.appolon.homelab.arr.core

import kotlinx.serialization.Serializable

/** The five supported *arr applications. */
enum class AppType(val wireName: String, val displayName: String) {
    RADARR("radarr", "Radarr"),
    SONARR("sonarr", "Sonarr"),
    LIDARR("lidarr", "Lidarr"),
    PROWLARR("prowlarr", "Prowlarr"),
    BAZARR("bazarr", "Bazarr"),
}

/** Failure taxonomy shared by every layer. Screens map these to message resources. */
sealed interface ArrError {
    val messageKey: String

    data class Network(val cause: Throwable?) : ArrError {
        override val messageKey = "arr_error_network"
    }

    data class Http(val code: Int, val bodySnippet: String?) : ArrError {
        override val messageKey = "arr_error_http"
    }

    data class Auth(val code: Int) : ArrError {
        override val messageKey = "arr_error_auth"
    }

    data class Parse(val cause: Throwable) : ArrError {
        override val messageKey = "arr_error_parse"
    }

    data class Offline(val actionId: String? = null) : ArrError {
        override val messageKey = "arr_error_offline"
    }

    data class Conflicted(val actionId: String, val code: Int) : ArrError {
        override val messageKey = "arr_error_conflict"
    }

    data class Unknown(val cause: Throwable?) : ArrError {
        override val messageKey = "arr_error_unknown"
    }
}

/** Result type used across repositories and view models. */
sealed interface ArrResult<out T> {
    data class Ok<T>(val value: T) : ArrResult<T>
    data class Err(val error: ArrError) : ArrResult<Nothing>

    companion object {
        fun <T> ok(value: T): ArrResult<T> = Ok(value)
        fun err(error: ArrError): ArrResult<Nothing> = Err(error)
    }
}

/** Generic page wrapper for endpoints returning paged or windowed results. */
data class ArrPage<T>(
    val items: List<T>,
    val total: Int? = null,
    val hasMore: Boolean = false,
)

/** Stable identifier of one configured server instance. */
@Serializable
@JvmInline
value class ServerId(val value: String)

/** Entity type discriminator for the offline entity store. Example: "radarr.movie". */
@Serializable
@JvmInline
value class EntityType(val value: String)

/** Client-side dedupe identity for replayed offline actions. */
@Serializable
@JvmInline
value class ActionId(val value: String)
