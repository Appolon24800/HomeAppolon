package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.CoverType

/**
 * Poster and banner URL resolution for the series library. Posters prefer the
 * server's local mediacover path (it rides the same connection path as the
 * API, proxy and trust included, and is fetched by the shared authenticated
 * image loader); remote TVDB art serves series with no local cover yet.
 * Banners prefer the remote URL per the feature brief, falling back to the
 * local banner cover for series whose only art is local. MediaCover endpoints
 * never go through Retrofit; Coil loads both shapes.
 */
object SeriesPosterUrls {

    /**
     * Server-generated resized poster variant (`poster-500.jpg`), small
     * enough for grid tiles and wanted row thumbs. Banners stay original:
     * Sonarr only generates resized poster variants reliably.
     */
    private const val LOCAL_SIZE_PX = 500

    /**
     * The poster URL for one series, or null when neither a local cover nor a
     * remote poster is known and the caller should show the monogram tile.
     */
    fun posterUrlOf(series: SeriesResource, baseUrl: String): String? {
        val images = series.images.orEmpty()
        if (images.any { it.coverType == MediaCoverTypes.POSTER }) {
            return baseUrl.trimEnd('/') + ArrImages.sonarrCover(
                series.id.toLong(),
                CoverType.POSTER,
                LOCAL_SIZE_PX,
            )
        }
        val remote = images.firstOrNull { it.coverType == MediaCoverTypes.POSTER }?.remoteUrl
        return ArrImages.tmdb(remote) ?: ArrImages.tmdb(series.remotePoster)
    }

    /**
     * The wide banner URL for one series (table rows, detail headers), or null
     * when no banner-shaped art exists.
     */
    fun bannerUrlOf(series: SeriesResource, baseUrl: String): String? {
        val images = series.images.orEmpty()
        val remote = images.firstOrNull { it.coverType == MediaCoverTypes.BANNER }?.remoteUrl
            ?: images.firstOrNull { it.coverType == MediaCoverTypes.FANART }?.remoteUrl
        ArrImages.tmdb(remote)?.let { return it }
        if (images.any { it.coverType == MediaCoverTypes.BANNER }) {
            return baseUrl.trimEnd('/') + ArrImages.sonarrCover(
                series.id.toLong(),
                CoverType.BANNER,
                LOCAL_SIZE_PX,
            )
        }
        return null
    }
}
