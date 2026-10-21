package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ImportRejectionResource wire contract. Dates remain unparsed strings. */
@Serializable
data class ImportRejectionResource(
    val reason: String? = null,
    val type: RejectionType = RejectionType.PERMANENT
)
