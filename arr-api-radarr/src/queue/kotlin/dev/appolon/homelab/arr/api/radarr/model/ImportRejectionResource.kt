package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr ImportRejectionResource. Dates remain unparsed strings. */
@Serializable
data class ImportRejectionResource(
    val reason: String? = null,
    val type: RejectionType = RejectionType.PERMANENT,
)
