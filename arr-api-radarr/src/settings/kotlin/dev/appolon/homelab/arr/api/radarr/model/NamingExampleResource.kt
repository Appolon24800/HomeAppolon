package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr NamingExampleResource. Dates remain unparsed strings. */
@Serializable
data class NamingExampleResource(
    val movieExample: String? = null,
    val movieFolderExample: String? = null,
)
