package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.CoverType

/**
 * Cover URL resolution for the artists library grid. Local covers win over
 * remote art because they travel the same connection path as the API itself
 * (proxy and trust included) and are authenticated by the shared image
 * loader; remote fanart URLs serve artists that have no local cover yet.
 * MediaCover endpoints never go through Retrofit.
 */
object ArtistPosterUrls {

    /** Original-size local cover; Lidarr 404s ungenerated resized variants. */
    private val LOCAL_SIZE_PX: Int? = null

    /**
     * The poster URL for one artist, or null when neither a local cover nor a
     * remote poster is known and the caller should show the monogram tile.
     */
    fun posterUrlOf(artist: ArtistResource, baseUrl: String): String? {
        val hasLocalPoster = artist.images.orEmpty()
            .any { it.coverType == MediaCoverTypes.POSTER }
        if (hasLocalPoster) {
            return baseUrl.trimEnd('/') + "/" +
                ArrImages.lidarrArtistCover(artist.id.toLong(), CoverType.POSTER, LOCAL_SIZE_PX)
        }
        val remote = artist.images.orEmpty()
            .firstOrNull { it.coverType == MediaCoverTypes.POSTER }
            ?.remoteUrl
            ?: artist.remotePoster
            ?: return null
        // Lidarr's remote art lives on absolute http(s) CDNs; anything else is
        // not fetchable, so let the caller fall back to the monogram.
        return remote.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
    }
}
