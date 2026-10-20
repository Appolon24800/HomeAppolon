package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesTranslationResource(
    val language: Language = Language(),
    val title: String? = null,
    val overview: String? = null
)
