package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.api.RadarrMediaCovers
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrImages

/**
 * Poster URL resolution for the library grid. Local covers win over CDN art
 * because they travel the same connection path as the API itself (proxy and
 * trust included) and are authenticated by the shared image loader; remote
 * TMDB URLs serve movies that have no local cover yet, typically list imports
 * and unreleased entries. MediaCover endpoints never go through Retrofit.
 */
object MoviePosterUrls {

    /**
     * Server-generated resized poster variant (`poster-500.jpg`): a third of
     * the bytes of the original for grid and table thumbnails, which render
     * at roughly 128dp wide. Detail hero art does not use this path.
     */
    private const val LOCAL_SIZE_PX = 500

    /**
     * The poster URL for one movie, or null when neither a local cover nor a
     * remote poster is known and the caller should show the monogram tile.
     * A poster entry counts as local only when it carries a server-relative
     * url; entries that only carry remoteUrl belong to movies whose cover
     * Radarr has not pulled yet.
     */
    fun posterUrlOf(movie: MovieResource, baseUrl: String): String? {
        val poster = movie.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.POSTER }
        val localUrl = poster?.url
        if (!localUrl.isNullOrBlank() && !localUrl.startsWith("http", ignoreCase = true)) {
            return baseUrl.trimEnd('/') + "/" +
                RadarrMediaCovers.path(movie.id, MediaCoverTypes.POSTER, LOCAL_SIZE_PX)
        }
        return ArrImages.tmdb(poster?.remoteUrl) ?: ArrImages.tmdb(movie.remotePoster)
    }
}
