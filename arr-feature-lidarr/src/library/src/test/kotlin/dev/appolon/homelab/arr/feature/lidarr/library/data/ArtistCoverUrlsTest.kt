package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCover
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.feature.lidarr.detail.data.ArtistDetailCoverUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistCoverUrlsTest {
    private val bases = listOf("http://host:8686", "http://host:8686/", "http://host:8686/lidarr", "http://host:8686/lidarr/")

    @Test
    fun `artist poster joins the API path with one slash and preserves the prefix`() {
        val artist = ArtistResource(id = 3, images = listOf(MediaCover(coverType = MediaCoverTypes.POSTER)))
        bases.forEach { base ->
            assertEquals("${base.trimEnd('/')}/api/v1/mediacover/artist/3/poster.jpg", ArtistPosterUrls.posterUrlOf(artist, base))
        }
    }

    @Test
    fun `artist hero joins banner and fanart paths with one slash`() {
        listOf(MediaCoverTypes.BANNER, MediaCoverTypes.FANART).forEach { type ->
            val artist = ArtistResource(id = 3, images = listOf(MediaCover(coverType = type)))
            bases.forEach { base ->
                val filename = type.name.lowercase() + ".jpg"
                assertEquals("${base.trimEnd('/')}/api/v1/mediacover/artist/3/$filename", ArtistDetailCoverUrls.heroUrlOf(artist, base))
            }
        }
    }

    @Test
    fun `detail album art joins the API path with one slash and preserves the prefix`() {
        val album = AlbumResource(id = 7, images = listOf(MediaCover(coverType = MediaCoverTypes.COVER)))
        bases.forEach { base ->
            assertEquals("${base.trimEnd('/')}/api/v1/mediacover/album/7/cover.jpg", ArtistDetailCoverUrls.albumCoverUrlOf(album, base))
        }
    }
}
