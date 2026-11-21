package dev.appolon.homelab.arr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagesTest {

    @Test
    fun tmdbBuildsFromBarePath() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/9O1Iy9odqMYv0eAPRzJtybrvd6E.jpg",
            ArrImages.tmdb("/9O1Iy9odqMYv0eAPRzJtybrvd6E.jpg"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/9O1Iy9odqMYv0eAPRzJtybrvd6E.jpg",
            ArrImages.tmdb("9O1Iy9odqMYv0eAPRzJtybrvd6E.jpg"),
        )
    }

    @Test
    fun tmdbSizes() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/abc.jpg",
            ArrImages.tmdb("/abc.jpg", TmdbImageSize.ORIGINAL),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w342/abc.jpg",
            ArrImages.tmdb("/abc.jpg", TmdbImageSize.W342),
        )
    }

    @Test
    fun tmdbPassthroughAndNull() {
        assertEquals(
            "https://art.someone.cdn/img.jpg",
            ArrImages.tmdb("https://art.someone.cdn/img.jpg"),
        )
        assertNull(ArrImages.tmdb(null))
        assertNull(ArrImages.tmdb("   "))
    }

    @Test
    fun tmdbSizeSwap() {
        val w500 = "https://image.tmdb.org/t/p/w500/9O1Iy9odqMYv0eAPRzJtybrvd6E.jpg"
        assertEquals(
            "https://image.tmdb.org/t/p/original/9O1Iy9odqMYv0eAPRzJtybrvd6E.jpg",
            ArrImages.tmdbSize(w500, TmdbImageSize.ORIGINAL),
        )
        // Non-TMDB hosts come back untouched.
        val other = "https://art.someone.cdn/w500/img.jpg"
        assertEquals(other, ArrImages.tmdbSize(other, TmdbImageSize.W185))
    }

    @Test
    fun internalCoverPaths() {
        assertEquals(
            "/api/v3/mediacover/27/poster.jpg",
            ArrImages.radarrCover(27, CoverType.POSTER),
        )
        assertEquals(
            "/api/v3/mediacover/27/poster-500.jpg",
            ArrImages.radarrCover(27, CoverType.POSTER, sizePx = 500),
        )
        assertEquals(
            "/api/v3/mediacover/42/fanart.jpg",
            ArrImages.sonarrCover(42, CoverType.FANART),
        )
        assertEquals(
            "/api/v3/mediacover/42/banner-360.jpg",
            ArrImages.sonarrCover(42, CoverType.BANNER, sizePx = 360),
        )
        assertEquals(
            "/api/v1/mediacover/artist/7/headshot.jpg",
            ArrImages.lidarrArtistCover(7, CoverType.HEADSHOT),
        )
        assertEquals(
            "/api/v1/mediacover/artist/7/logo-500.jpg",
            ArrImages.lidarrArtistCover(7, CoverType.LOGO, sizePx = 500),
        )
        assertEquals(
            "/api/v1/mediacover/album/99/cover.jpg",
            ArrImages.lidarrAlbumCover(99, CoverType.COVER),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun coverRejectsNonPositiveSize() {
        ArrImages.radarrCover(1, CoverType.POSTER, sizePx = 0)
    }

    @Test
    fun serverUrlDoesNotDoubleTheUrlBase() {
        // Real Radarr behind /radarr reports covers with the base included.
        assertEquals(
            "http://host:30025/radarr/MediaCover/1454/poster.jpg?h=1",
            ArrImages.serverUrl("http://host:30025/radarr/", "/radarr/MediaCover/1454/poster.jpg?h=1"),
        )
        assertEquals(
            "http://host:30046/bazarr/images/movies/radarr/MediaCover/1/poster-500.jpg",
            ArrImages.serverUrl("http://host:30046/bazarr", "/bazarr/images/movies/radarr/MediaCover/1/poster-500.jpg"),
        )
    }

    @Test
    fun serverUrlJoinsPathsWithoutTheBase() {
        assertEquals(
            "http://host:30025/radarr/MediaCover/1/poster.jpg",
            ArrImages.serverUrl("http://host:30025/radarr", "/MediaCover/1/poster.jpg"),
        )
        // A sibling prefix is not the base: /radarr4k must not match /radarr.
        assertEquals(
            "http://host/radarr/radarr4k/x.jpg",
            ArrImages.serverUrl("http://host/radarr", "/radarr4k/x.jpg"),
        )
        assertEquals("http://host/x.jpg", ArrImages.serverUrl("http://host", "/x.jpg"))
        assertEquals("http://host/x.jpg", ArrImages.serverUrl("http://host/", "x.jpg"))
        assertEquals("https://cdn.example/x.jpg", ArrImages.serverUrl("http://host", "https://cdn.example/x.jpg"))
        assertNull(ArrImages.serverUrl("http://host", "  "))
        assertNull(ArrImages.serverUrl("http://host", null))
    }
}
