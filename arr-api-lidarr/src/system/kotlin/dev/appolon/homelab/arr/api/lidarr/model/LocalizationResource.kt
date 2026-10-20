package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr LocalizationResource wire resource holding the server string table. */
@Serializable
data class LocalizationResource(
    val id: Int = 0,
    val strings: Map<String, String?>? = null,
)
