package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Settings singleton behind the DownloadClientConfig tag. The spec ships no
 * download-client-wide options beyond the row id, so the resource is the id
 * alone; the server always serves the row with id 1.
 */
@Serializable
data class DownloadClientConfigResource(
    val id: Int = 1,
)
