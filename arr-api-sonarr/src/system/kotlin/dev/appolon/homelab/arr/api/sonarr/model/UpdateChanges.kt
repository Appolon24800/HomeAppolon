package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Changelog carried by an UpdateResource. */
@Serializable
data class UpdateChanges(
    val `new`: List<String>? = null,
    val fixed: List<String>? = null
)
