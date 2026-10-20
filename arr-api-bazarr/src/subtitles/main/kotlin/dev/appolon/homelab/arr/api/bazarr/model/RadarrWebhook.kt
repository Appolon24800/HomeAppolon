package dev.appolon.homelab.arr.api.bazarr.model

import kotlinx.serialization.Serializable

@Serializable
data class RadarrWebhook(
    val eventType: String,
    val movieFile: RadarrMovieFile? = null,
    val movie: RadarrMovie? = null,
)
