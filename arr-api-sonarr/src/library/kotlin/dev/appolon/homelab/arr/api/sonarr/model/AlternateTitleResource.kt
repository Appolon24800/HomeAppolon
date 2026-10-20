package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

@Serializable
data class AlternateTitleResource(
    val title: String? = null,
    val seasonNumber: Int? = null,
    val sceneSeasonNumber: Int? = null,
    val sceneOrigin: String? = null,
    val comment: String? = null
)
