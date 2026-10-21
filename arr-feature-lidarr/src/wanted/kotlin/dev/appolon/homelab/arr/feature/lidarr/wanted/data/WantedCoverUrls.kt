package dev.appolon.homelab.arr.feature.lidarr.wanted.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.CoverType

/**
 * Cover URL resolution for the wanted album rows. Local covers win over
 * remote art because they travel the same connection path as the API itself
 * (proxy and trust included) and are authenticated by the shared image
 * loader; remote URLs serve albums whose local cover has not been written
 * yet. MediaCover endpoints never go through Retrofit.
 */
object WantedCoverUrls {

    /** Original-size local cover; Lidarr 404s ungenerated resized variants. */
    private val LOCAL_SIZE_PX: Int? = null

    /**
     * The cover URL for one album, or null when neither a local cover nor a
     * remote image is known and the caller should show the placeholder tile.
     */
    fun coverUrlOf(album: AlbumResource, baseUrl: String): String? {
        if (album.images.orEmpty().any { it.coverType == MediaCoverTypes.COVER }) {
            // ArrImages paths start with '/', so the trimmed base joins cleanly.
            return baseUrl.trimEnd('/') +
                ArrImages.lidarrAlbumCover(album.id.toLong(), CoverType.COVER, LOCAL_SIZE_PX)
        }
        val remote = album.remoteCover ?: return null
        // Lidarr's remote art lives on absolute http(s) CDNs; anything else is
        // not fetchable, so let the caller fall back to the placeholder.
        return remote.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
    }
}
