package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr ImportListConfigResource wire contract for the list settings screen. */
@Serializable
data class ImportListConfigResource(
    val id: Int = 0,
    val listSyncLevel: ListSyncLevelType = ListSyncLevelType.DISABLED,
    val listSyncTag: Int = 0
)
