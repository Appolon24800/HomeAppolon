package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/**
 * Sonarr notification provider. `fields` carries the provider-specific settings
 * contract; values arrive as arbitrary JSON keyed by `field.name`. The
 * `supports*` booleans describe what the implementation can do; the `on*`
 * booleans are the user's triggers.
 */
@Serializable
data class NotificationResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: Set<Int>? = null,
    val presets: List<NotificationResource>? = null,
    val link: String? = null,
    val onGrab: Boolean = false,
    val onDownload: Boolean = false,
    val onUpgrade: Boolean = false,
    val onImportComplete: Boolean = false,
    val onRename: Boolean = false,
    val onSeriesAdd: Boolean = false,
    val onSeriesDelete: Boolean = false,
    val onEpisodeFileDelete: Boolean = false,
    val onEpisodeFileDeleteForUpgrade: Boolean = false,
    val onHealthIssue: Boolean = false,
    val includeHealthWarnings: Boolean = false,
    val onHealthRestored: Boolean = false,
    val onApplicationUpdate: Boolean = false,
    val onManualInteractionRequired: Boolean = false,
    val supportsOnGrab: Boolean = false,
    val supportsOnDownload: Boolean = false,
    val supportsOnUpgrade: Boolean = false,
    val supportsOnImportComplete: Boolean = false,
    val supportsOnRename: Boolean = false,
    val supportsOnSeriesAdd: Boolean = false,
    val supportsOnSeriesDelete: Boolean = false,
    val supportsOnEpisodeFileDelete: Boolean = false,
    val supportsOnEpisodeFileDeleteForUpgrade: Boolean = false,
    val supportsOnHealthIssue: Boolean = false,
    val supportsOnHealthRestored: Boolean = false,
    val supportsOnApplicationUpdate: Boolean = false,
    val supportsOnManualInteractionRequired: Boolean = false,
    val testCommand: String? = null
)
