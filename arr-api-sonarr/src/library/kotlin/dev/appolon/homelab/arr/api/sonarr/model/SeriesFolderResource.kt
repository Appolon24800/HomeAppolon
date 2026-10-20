package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesFolderResource(
    val language: Language = Language(),
    val folder: String? = null
)
