package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/**
 * Sonarr MetadataResource wire contract. `fields` carries the provider
 * specific settings contract. Reuses Field and ProviderMessage from the
 * library and discovery groups.
 */
@Serializable
data class MetadataResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: Set<Int>? = null,
    val presets: List<MetadataResource>? = null,
    val enable: Boolean = false
)
