package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr QualityDefinitionResource wire resource. Dates remain unparsed strings. */
@Serializable
data class QualityDefinitionResource(
    val id: Int = 0,
    val quality: Quality? = null,
    val title: String? = null,
    val weight: Int = 0,
    val minSize: Double? = null,
    val maxSize: Double? = null,
    val preferredSize: Double? = null,
)
