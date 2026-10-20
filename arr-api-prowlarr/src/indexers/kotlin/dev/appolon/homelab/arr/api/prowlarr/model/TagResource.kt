package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/** A free-form label attachable to indexers, proxies and applications. */
@Serializable
data class TagResource(
    val id: Int = 0,
    val label: String? = null,
)
