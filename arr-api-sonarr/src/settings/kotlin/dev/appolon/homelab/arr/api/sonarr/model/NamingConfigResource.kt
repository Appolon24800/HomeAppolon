package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr NamingConfigResource wire contract. */
@Serializable
data class NamingConfigResource(
    val id: Int = 0,
    val renameEpisodes: Boolean = false,
    val replaceIllegalCharacters: Boolean = false,
    val colonReplacementFormat: Int = 0,
    val customColonReplacementFormat: String? = null,
    val multiEpisodeStyle: Int = 0,
    val standardEpisodeFormat: String? = null,
    val dailyEpisodeFormat: String? = null,
    val animeEpisodeFormat: String? = null,
    val seriesFolderFormat: String? = null,
    val seasonFolderFormat: String? = null,
    val specialsFolderFormat: String? = null
)
