package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStatisticsResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistLibraryFiltersTest {

    private val now: Instant = Instant.parse("2026-10-20T00:00:00Z")

    private fun album(
        id: Int,
        artistId: Int,
        releaseDate: String?,
        monitored: Boolean = true,
        trackFileCount: Int = 1,
    ) = AlbumResource(
        id = id,
        artistId = artistId,
        title = "Album $id",
        releaseDate = releaseDate,
        monitored = monitored,
        statistics = AlbumStatisticsResource(trackFileCount = trackFileCount, trackCount = 10),
    )

    private fun artist(
        id: Int,
        name: String,
        monitored: Boolean = true,
        quality: Int = 1,
        metadata: Int = 1,
        genres: List<String> = emptyList(),
        tags: List<Int> = emptyList(),
        path: String? = "/music/$name",
        statistics: dev.appolon.homelab.arr.api.lidarr.model.ArtistStatisticsResource? = null,
    ) = ArtistResource(
        id = id,
        artistName = name,
        sortName = name.lowercase(),
        monitored = monitored,
        qualityProfileId = quality,
        metadataProfileId = metadata,
        genres = genres,
        tags = tags,
        path = path,
        rootFolderPath = "/music",
        statistics = statistics,
    )

    private val artists = listOf(
        artist(1, "Alpha", monitored = true, quality = 1, metadata = 1, genres = listOf("Rock"), tags = listOf(1)),
        artist(2, "Beta", monitored = false, quality = 2, metadata = 2, genres = listOf("Jazz"), tags = listOf(2)),
        artist(3, "Gamma", monitored = true, quality = 1, metadata = 3, genres = listOf("Rock"), tags = listOf(1, 2)),
    )

    private val albums = listOf(
        album(11, 1, "2020-01-01", monitored = true, trackFileCount = 1),
        album(12, 1, "2027-06-01", monitored = true, trackFileCount = 0),
        album(21, 2, null, monitored = true, trackFileCount = 0),
        album(31, 3, "2024-03-03", monitored = false, trackFileCount = 0),
    )

    private val summaries = artistAlbumSummaries(albums, now)

    @Test
    fun `summaries bucket albums by release status and missing`() {
        val alpha = summaries.getValue(1)
        assertEquals(2, alpha.albumCount)
        assertEquals(1, alpha.released)
        assertEquals(1, alpha.upcoming)
        assertEquals(1, alpha.missing)
        val beta = summaries.getValue(2)
        assertEquals(1, beta.tba)
        assertEquals(1, beta.missing)
        // Album 31 is unmonitored, so it never counts as missing.
        assertEquals(0, summaries.getValue(3).missing)
    }

    @Test
    fun `options derive from the artists in view`() {
        val options = ArtistLibraryFilters.optionsFrom(artists, summaries)
        assertEquals(listOf("Jazz", "Rock"), options.genres)
        assertEquals(listOf(1, 2), options.qualityProfileIds)
        assertEquals(listOf(1, 2, 3), options.metadataProfileIds)
        assertEquals(listOf(1, 2), options.tagIds)
        assertEquals(2.0, options.maxAlbums, 0.0)
        // folderOf prefers rootFolderPath, which every fixture shares.
        assertEquals(listOf("/music"), options.folders)
    }

    @Test
    fun `monitored filter keeps only matching artists`() {
        val state = FilterState().with(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(true))
        val presented = presentArtists(artists, ArtistLibraryFilters.optionsFrom(artists), summaries, state, "title", false, 60)
        assertEquals(listOf(1, 3), presented.all.map { it.id })
    }

    @Test
    fun `missing filter uses the album rollup`() {
        val state = FilterState().with(ArtistLibraryFilters.MISSING, FilterValue.Toggle(true))
        val options = ArtistLibraryFilters.optionsFrom(artists, summaries)
        val presented = presentArtists(artists, options, summaries, state, "title", false, 60)
        assertEquals(listOf(1, 2), presented.all.map { it.id })
    }

    @Test
    fun `missing filter with no album data matches nothing`() {
        val state = FilterState().with(ArtistLibraryFilters.MISSING, FilterValue.Toggle(true))
        val options = ArtistLibraryFilters.optionsFrom(artists)
        val presented = presentArtists(artists, options, emptyMap(), state, "title", false, 60)
        assertTrue(presented.all.isEmpty())
    }

    @Test
    fun `quality and metadata filters select by profile id`() {
        val options = ArtistLibraryFilters.optionsFrom(artists, summaries)
        val quality = FilterState().with(ArtistLibraryFilters.QUALITY, FilterValue.Choice(setOf("2")))
        assertEquals(
            listOf(2),
            presentArtists(artists, options, summaries, quality, "title", false, 60).all.map { it.id },
        )
        val metadata = FilterState().with(ArtistLibraryFilters.METADATA, FilterValue.Choice(setOf("1", "3")))
        assertEquals(
            listOf(1, 3),
            presentArtists(artists, options, summaries, metadata, "title", false, 60).all.map { it.id },
        )
    }

    @Test
    fun `genre, folder, and tags filters intersect`() {
        val options = ArtistLibraryFilters.optionsFrom(artists, summaries)
        val state = FilterState()
            .with(ArtistLibraryFilters.GENRE, FilterValue.Choice(setOf("Rock")))
            .with(ArtistLibraryFilters.TAGS, FilterValue.Choice(setOf("2")))
        assertEquals(
            listOf(3),
            presentArtists(artists, options, summaries, state, "title", false, 60).all.map { it.id },
        )
    }

    @Test
    fun `album count range filters on the rollup count`() {
        val options = ArtistLibraryFilters.optionsFrom(artists, summaries)
        val exactlyTwo = FilterState().with(ArtistLibraryFilters.ALBUMS, FilterValue.Range(2.0, 2.0))
        assertEquals(
            listOf(1),
            presentArtists(artists, options, summaries, exactlyTwo, "title", false, 60).all.map { it.id },
        )
        val atLeastOne = FilterState().with(ArtistLibraryFilters.ALBUMS, FilterValue.Range(1.0, null))
        assertEquals(
            listOf(1, 2, 3),
            presentArtists(artists, options, summaries, atLeastOne, "title", false, 60).all.map { it.id },
        )
    }

    @Test
    fun `album count falls back to artist statistics without a rollup`() {
        val withStats = listOf(
            artist(1, "Alpha", statistics = ArtistStatisticsResourceForTests(5)),
        )
        val options = ArtistLibraryFilters.optionsFrom(withStats)
        assertEquals(5, ArtistLibraryFilters.albumCountOf(withStats.single(), emptyMap()))
        assertEquals(5.0, options.maxAlbums, 0.0)
    }

    @Test
    fun `presentation sorts and windows the result`() {
        val options = ArtistLibraryFilters.optionsFrom(artists, summaries)
        // Album counts: artist 1 has 2, artists 2 and 3 have 1 each, insertion order breaks the tie.
        val byAlbums = presentArtists(artists, options, summaries, FilterState(), "albums", false, 60)
        assertEquals(listOf(2, 3, 1), byAlbums.all.map { it.id })
        val windowed = presentArtists(artists, options, summaries, FilterState(), "albums", false, 2)
        assertEquals(listOf(2, 3), windowed.visible.map { it.id })
        assertFalse(windowed.endReached)
    }

    @Test
    fun `activeCount counts only narrowing filters`() {
        val state = FilterState()
            .with(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(null))
            .with(ArtistLibraryFilters.GENRE, FilterValue.Choice(setOf("Rock")))
        val ids = listOf(ArtistLibraryFilters.MONITORED, ArtistLibraryFilters.GENRE)
        assertEquals(1, ArtistLibraryFilters.activeCount(ids, state))
    }

    private fun ArtistStatisticsResourceForTests(albumCount: Int) =
        dev.appolon.homelab.arr.api.lidarr.model.ArtistStatisticsResource(albumCount = albumCount)
}
