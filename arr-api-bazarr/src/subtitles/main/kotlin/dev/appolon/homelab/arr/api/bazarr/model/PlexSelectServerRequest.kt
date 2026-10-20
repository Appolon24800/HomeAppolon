package dev.appolon.homelab.arr.api.bazarr.model

import kotlinx.serialization.Serializable

/** The spec declares connections as a string, not an array of connection objects. */
@Serializable
data class PlexSelectServerRequest(val connections: String? = null)
