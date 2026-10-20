package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr NotificationResource.
 * Dates remain unparsed strings. Provider settings arrive as dynamic [fields]. */
@Serializable
data class NotificationResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage? = null,
    val tags: Set<Int>? = null,
    val presets: List<NotificationResource>? = null,
    val link: String? = null,
    val onGrab: Boolean = false,
    val onDownload: Boolean = false,
    val onUpgrade: Boolean = false,
    val onRename: Boolean = false,
    val onMovieAdded: Boolean = false,
    val onMovieDelete: Boolean = false,
    val onMovieFileDelete: Boolean = false,
    val onMovieFileDeleteForUpgrade: Boolean = false,
    val onHealthIssue: Boolean = false,
    val includeHealthWarnings: Boolean = false,
    val onHealthRestored: Boolean = false,
    val onApplicationUpdate: Boolean = false,
    val onManualInteractionRequired: Boolean = false,
    val supportsOnGrab: Boolean = false,
    val supportsOnDownload: Boolean = false,
    val supportsOnUpgrade: Boolean = false,
    val supportsOnRename: Boolean = false,
    val supportsOnMovieAdded: Boolean = false,
    val supportsOnMovieDelete: Boolean = false,
    val supportsOnMovieFileDelete: Boolean = false,
    val supportsOnMovieFileDeleteForUpgrade: Boolean = false,
    val supportsOnHealthIssue: Boolean = false,
    val supportsOnHealthRestored: Boolean = false,
    val supportsOnApplicationUpdate: Boolean = false,
    val supportsOnManualInteractionRequired: Boolean = false,
    val testCommand: String? = null,
)
