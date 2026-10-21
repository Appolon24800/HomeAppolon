package dev.appolon.homelab.arr.feature.lidarr.detail.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.CoverType

/**
 * Cover URL resolution for the artist detail screen. Local covers win over
 * remote art because they travel the same connection path as the API itself
 * (proxy and trust included) and are authenticated by the shared image
 * loader. MediaCover endpoints never go through Retrofit.
 */
object ArtistDetailCoverUrls {

    /** Original-size local cover; Lidarr 404s ungenerated resized variants. */
    private val LOCAL_SIZE_PX: Int? = null

    /**
     * Wide hero art for the artist: the local banner first, then local
     * fanart, then the remote banner or fanart URL, or null when only a
     * poster exists and the caller should show the plain color header.
     */
    fun heroUrlOf(artist: ArtistResource, baseUrl: String): String? {
        val images = artist.images.orEmpty()
        val hasLocal = images.any {
            it.coverType == MediaCoverTypes.BANNER || it.coverType == MediaCoverTypes.FANART
        }
        if (hasLocal) {
            val type = if (images.any { it.coverType == MediaCoverTypes.BANNER }) {
                CoverType.BANNER
            } else {
                CoverType.FANART
            }
            return baseUrl.trimEnd('/') + "/" +
                ArrImages.lidarrArtistCover(artist.id.toLong(), type, LOCAL_SIZE_PX)
        }
        return images.firstOrNull {
            it.coverType == MediaCoverTypes.BANNER || it.coverType == MediaCoverTypes.FANART
        }?.remoteUrl?.let(::absolute)
    }

    /** Square album cover for the album rows. */
    fun albumCoverUrlOf(album: AlbumResource, baseUrl: String): String? {
        val hasLocal = album.images.orEmpty().any { it.coverType == MediaCoverTypes.COVER }
        if (hasLocal) {
            return baseUrl.trimEnd('/') + "/" +
                ArrImages.lidarrAlbumCover(album.id.toLong(), CoverType.COVER, LOCAL_SIZE_PX)
        }
        return album.images.orEmpty()
            .firstOrNull { it.coverType == MediaCoverTypes.COVER }
            ?.remoteUrl
            ?.let(::absolute)
            ?: album.remoteCover?.let(::absolute)
    }

    /** Remote art lives on absolute http(s) CDNs; anything else is not fetchable. */
    private fun absolute(url: String): String? = url.takeIf {
        it.startsWith("http://", ignoreCase = true) ||
            it.startsWith("https://", ignoreCase = true)
    }
}
