package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr MetadataResource wire resource for a metadata consumer. */
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
    val enable: Boolean = false,
)
