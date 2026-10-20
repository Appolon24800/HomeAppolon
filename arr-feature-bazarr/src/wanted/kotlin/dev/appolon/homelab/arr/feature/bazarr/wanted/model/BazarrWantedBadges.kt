package dev.appolon.homelab.arr.feature.bazarr.wanted.model

import kotlinx.serialization.Serializable

/**
 * Total missing-subtitle counts from the Bazarr badges endpoint. The response
 * has no schema in the bundled spec, so both fields default to zero when the
 * payload is missing or renamed.
 */
@Serializable
data class BazarrWantedBadges(
    val episodes: Int = 0,
    val movies: Int = 0,
)
