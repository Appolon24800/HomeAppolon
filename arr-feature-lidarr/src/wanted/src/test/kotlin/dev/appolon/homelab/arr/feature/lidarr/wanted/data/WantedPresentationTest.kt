package dev.appolon.homelab.arr.feature.lidarr.wanted.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCover
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WantedPresentationTest {

    private fun album(
        id: Int,
        artistId: Int,
        artistName: String?,
        title: String,
        releaseDate: String? = "2026-01-01",
        albumType: String? = "Album",
        monitored: Boolean = true,
    ) = AlbumResource(
        id = id,
        title = title,
        artistId = artistId,
        albumType = albumType,
        releaseDate = releaseDate,
        monitored = monitored,
        artist = artistName?.let {
            ArtistResource(id = artistId, artistName = it, sortName = it.lowercase())
        },
    )

    private val albums = listOf(
        album(1, 20, "Beta", "Zeta", releaseDate = "2026-02-01", monitored = true),
        album(2, 10, "Alpha", "Beta", albumType = "Single", monitored = false),
        album(3, 10, "Alpha", "Alpha", albumType = "EP", monitored = true),
        album(4, 30, "Gamma", "Gamma", releaseDate = "2025-12-31", monitored = true),
    )

    @Test
    fun `unfiltered presentation sorts by artist then release date then title`() {
        val options = WantedFilters.optionsFrom(albums)
        val presented = presentWanted(albums, options, FilterState())
        // Alpha (two records, one per release date), Beta, Gamma.
        assertEquals(listOf(3, 2, 1, 4), presented.all.map { it.id })
    }

    @Test
    fun `albums without artists sort last but stay stable`() {
        val anonymous = AlbumResource(id = 9, artistId = 77, title = "Nameless")
        val options = WantedFilters.optionsFrom(albums + anonymous)
        val presented = presentWanted(albums + anonymous, options, FilterState())
        assertEquals(9, presented.all.last().id)
    }

    @Test
    fun `monitored toggle keeps only the chosen side`() {
        val options = WantedFilters.optionsFrom(albums)
        val on = presentWanted(
            albums, options,
            FilterState().with(WantedFilters.MONITORED, FilterValue.Toggle(true)),
        )
        assertEquals(listOf(3, 1, 4), on.all.map { it.id })
        val off = presentWanted(
            albums, options,
            FilterState().with(WantedFilters.MONITORED, FilterValue.Toggle(false)),
        )
        assertEquals(listOf(2), off.all.map { it.id })
    }

    @Test
    fun `release type choice matches wire values case insensitively`() {
        val options = WantedFilters.optionsFrom(albums)
        val singles = presentWanted(
            albums, options,
            FilterState().with(WantedFilters.RELEASE_TYPE, FilterValue.Choice(setOf("single"))),
        )
        assertEquals(listOf(2), singles.all.map { it.id })
        val eps = presentWanted(
            albums, options,
            FilterState().with(WantedFilters.RELEASE_TYPE, FilterValue.Choice(setOf("ep"))),
        )
        assertEquals(listOf(3), eps.all.map { it.id })
    }

    @Test
    fun `release type without a wire value never matches`() {
        val untapped = AlbumResource(id = 8, artistId = 10, title = "Untapped")
        val options = WantedFilters.optionsFrom(albums + untapped)
        val filtered = presentWanted(
            albums + untapped, options,
            FilterState().with(WantedFilters.RELEASE_TYPE, FilterValue.Choice(setOf("album"))),
        )
        assertFalse(filtered.all.any { it.id == 8 })
    }

    @Test
    fun `release type and monitored filters combine with and`() {
        val options = WantedFilters.optionsFrom(albums)
        val filtered = presentWanted(
            albums, options,
            FilterState()
                .with(WantedFilters.RELEASE_TYPE, FilterValue.Choice(setOf("single", "ep")))
                .with(WantedFilters.MONITORED, FilterValue.Toggle(true)),
        )
        assertEquals(listOf(3), filtered.all.map { it.id })
    }

    @Test
    fun `options carry release types in canonical order then extras`() {
        val mixed = albums + album(5, 10, "Alpha", "Live", albumType = "Broadcast")
        val options = WantedFilters.optionsFrom(mixed)
        assertEquals(listOf("album", "single", "ep", "broadcast"), options.releaseTypes)
    }

    @Test
    fun `specs and definitions stay id aligned`() {
        val options = WantedFilters.optionsFrom(albums)
        val specs = WantedFilters.specs(options)
        val definitions = WantedFilters.definitions(options)
        assertEquals(specs.map { it.id }, definitions.map { it.spec.id })
        assertTrue(
            specs.first { it.id == WantedFilters.RELEASE_TYPE } is
                dev.appolon.homelab.arr.core.FilterSpec.Choice,
        )
    }

    @Test
    fun `display helpers fall back to ids`() {
        assertEquals("Alpha", artistNameOf(album(1, 10, "Alpha", "A")))
        assertEquals("#77", artistNameOf(AlbumResource(id = 1, artistId = 77)))
        assertEquals("Alpha", albumTitleOf(album(1, 10, "Alpha", "Alpha")))
        assertEquals("#9", albumTitleOf(AlbumResource(id = 9, artistId = 1)))
    }

    @Test
    fun `release type normalization lowercases the wire value`() {
        assertEquals("album", releaseTypeOf(album(1, 1, "A", "A", albumType = "Album")))
        assertEquals("ep", releaseTypeOf(album(1, 1, "A", "A", albumType = "EP")))
        assertNull(releaseTypeOf(album(1, 1, "A", "A", albumType = null)))
        assertNull(releaseTypeOf(album(1, 1, "A", "A", albumType = "  ")))
    }

    @Test
    fun `tab meta reports end reached against cached rows`() {
        val meta = WantedTabMeta(totalRecords = 100, pagesLoaded = 2)
        assertFalse(meta.endReached(99))
        assertTrue(meta.endReached(100))
    }
}

class WantedCoverUrlsTest {

    private fun cover(coverType: MediaCoverTypes = MediaCoverTypes.COVER, remoteUrl: String? = null) =
        MediaCover(coverType = coverType, remoteUrl = remoteUrl)

    @Test
    fun `local cover wins and resolves against the base url with prefix`() {
        val album = AlbumResource(id = 12, images = listOf(cover()))
        assertEquals(
            "http://host:30025/lidarr/api/v1/mediacover/album/12/cover.jpg",
            WantedCoverUrls.coverUrlOf(album, "http://host:30025/lidarr/"),
        )
    }

    @Test
    fun `remote cover serves albums without local art`() {
        val album = AlbumResource(
            id = 12,
            images = listOf(cover(coverType = MediaCoverTypes.DISC)),
            remoteCover = "https://cdn.example.com/a.jpg",
        )
        assertEquals("https://cdn.example.com/a.jpg", WantedCoverUrls.coverUrlOf(album, "http://h/"))
    }

    @Test
    fun `remoteCover is the last resort and must be http`() {
        val album = AlbumResource(id = 12, remoteCover = "https://cdn.example.com/b.jpg")
        assertEquals("https://cdn.example.com/b.jpg", WantedCoverUrls.coverUrlOf(album, "http://h/"))
        val unreachable = AlbumResource(id = 12, remoteCover = "ftp://nope/a.jpg")
        assertNull(WantedCoverUrls.coverUrlOf(unreachable, "http://h/"))
    }

    @Test
    fun `albums without any art resolve to null`() {
        assertNull(WantedCoverUrls.coverUrlOf(AlbumResource(id = 12), "http://h/"))
    }
}
