package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr NamingConfigResource. Dates remain unparsed strings. */
@Serializable
data class NamingConfigResource(
    val id: Int = 0,
    val renameMovies: Boolean = false,
    val replaceIllegalCharacters: Boolean = false,
    val colonReplacementFormat: ColonReplacementFormat? = null,
    val standardMovieFormat: String? = null,
    val movieFolderFormat: String? = null,
)
