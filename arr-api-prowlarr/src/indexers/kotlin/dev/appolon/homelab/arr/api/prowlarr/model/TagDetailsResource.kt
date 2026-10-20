package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * A tag plus everywhere it is used. Usage id lists are server-computed, so
 * they are read-only information for the delete-confirmation UI.
 */
@Serializable
data class TagDetailsResource(
    val id: Int = 0,
    val label: String? = null,
    val notificationIds: List<Int>? = null,
    val indexerIds: List<Int>? = null,
    val indexerProxyIds: List<Int>? = null,
    val applicationIds: List<Int>? = null,
)
