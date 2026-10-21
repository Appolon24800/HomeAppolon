package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Maps one Prowlarr category set onto a client-side category name, so
 * releases pushed through this [DownloadClientResource] land with the
 * client's own label while keeping the newznab category ids.
 */
@Serializable
data class DownloadClientCategory(
    val clientCategory: String? = null,
    val categories: List<Int>? = null,
)
