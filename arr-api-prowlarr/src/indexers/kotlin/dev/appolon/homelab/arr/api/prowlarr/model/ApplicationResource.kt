package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How aggressively Prowlarr syncs indexers into a connected application. */
@Serializable
enum class ApplicationSyncLevel {
    @SerialName("disabled") DISABLED,
    @SerialName("addOnly") ADD_ONLY,
    @SerialName("fullSync") FULL_SYNC,
}

/**
 * One connected application (Radarr, Sonarr, Lidarr, ...) that receives
 * indexer syncs. Credentials live inside [fields], never in dedicated
 * properties, so they must be read through the Field machinery.
 */
@Serializable
data class ApplicationResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: List<Int>? = null,
    val presets: List<ApplicationResource>? = null,
    val syncLevel: ApplicationSyncLevel = ApplicationSyncLevel.DISABLED,
    val testCommand: String? = null,
)
