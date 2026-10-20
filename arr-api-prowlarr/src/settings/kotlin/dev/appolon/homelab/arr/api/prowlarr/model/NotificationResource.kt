package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * One notification definition (from the schema endpoint) or configured
 * instance. Prowlarr has no notification bulk route, so edits go through the
 * single-resource endpoints only. Event toggles pair with their
 * supportsOn... capability flag; the UI hides unsupported events.
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
    val tags: List<Int>? = null,
    val presets: List<NotificationResource>? = null,
    val link: String? = null,
    val onGrab: Boolean = false,
    val onHealthIssue: Boolean = false,
    val onHealthRestored: Boolean = false,
    val onApplicationUpdate: Boolean = false,
    val supportsOnGrab: Boolean = false,
    val includeManualGrabs: Boolean = false,
    val supportsOnHealthIssue: Boolean = false,
    val supportsOnHealthRestored: Boolean = false,
    val includeHealthWarnings: Boolean = false,
    val supportsOnApplicationUpdate: Boolean = false,
    val testCommand: String? = null,
)
