package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MediaCover
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieCollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddNewLogicTest {

    @Test
    fun `parses a parenthesized year`() {
        val parsed = AddNewLogic.parseSearchQuery("Dune (1984)")
        assertEquals("Dune", parsed.term)
        assertEquals(1984, parsed.year)
    }

    @Test
    fun `parses a bracketed year`() {
        val parsed = AddNewLogic.parseSearchQuery("Dune [2021]")
        assertEquals("Dune", parsed.term)
        assertEquals(2021, parsed.year)
    }

    @Test
    fun `parses a trailing bare year`() {
        val parsed = AddNewLogic.parseSearchQuery("batman 1989")
        assertEquals("batman", parsed.term)
        assertEquals(1989, parsed.year)
    }

    @Test
    fun `keeps numbers that are not years`() {
        val parsed = AddNewLogic.parseSearchQuery("Blade Runner 2049 sequel")
        assertEquals("Blade Runner 2049 sequel", parsed.term)
        assertNull(parsed.year)
    }

    @Test
    fun `a lone year stays a term`() {
        val parsed = AddNewLogic.parseSearchQuery("1984")
        assertEquals("1984", parsed.term)
        assertNull(parsed.year)
    }

    @Test
    fun `blank query parses to empty`() {
        val parsed = AddNewLogic.parseSearchQuery("   ")
        assertEquals("", parsed.term)
        assertNull(parsed.year)
    }

    @Test
    fun `rankByYear moves the matching year first and keeps the rest`() {
        val results = listOf(
            MovieResource(title = "Dune", year = 2021, tmdbId = 1),
            MovieResource(title = "Dune", year = 1984, tmdbId = 2),
            MovieResource(title = "Dune", year = 2000, tmdbId = 3),
        )
        val ranked = AddNewLogic.rankByYear(results, 1984) { it.year }
        assertEquals(listOf(1984, 2021, 2000), ranked.map { it.year })
        assertEquals(1984, ranked.first().year)
    }

    @Test
    fun `discovery sections split by flags without duplicates`() {
        val both = ImportListMoviesResource(tmdbId = 1, isPopular = true, isTrending = true)
        val recommendation = ImportListMoviesResource(tmdbId = 2, isRecommendation = true)
        val popular = ImportListMoviesResource(tmdbId = 3, isPopular = true)
        val none = ImportListMoviesResource(tmdbId = 4)
        val sections = AddNewLogic.discoverySections(listOf(both, recommendation, popular, none))
        assertEquals(listOf(2), sections.recommended.map { it.tmdbId })
        // `both` counts as trending because recommendations are claimed first.
        assertEquals(listOf(1), sections.trending.map { it.tmdbId })
        assertEquals(listOf(3), sections.popular.map { it.tmdbId })
        assertFalse(sections.isEmpty)
    }

    @Test
    fun `missingMovieIds keeps only fileless library movies of the collection`() {
        val movies = listOf(
            MovieResource(
                id = 1,
                tmdbId = 11,
                hasFile = false,
                collection = MovieCollectionResource(tmdbId = 5),
            ),
            MovieResource(
                id = 2,
                tmdbId = 12,
                hasFile = true,
                collection = MovieCollectionResource(tmdbId = 5),
            ),
            MovieResource(
                id = 3,
                tmdbId = 13,
                hasFile = false,
                collection = MovieCollectionResource(tmdbId = 9),
            ),
            MovieResource(
                id = 0,
                tmdbId = 14,
                hasFile = false,
                collection = MovieCollectionResource(tmdbId = 5),
            ),
        )
        assertEquals(listOf(1), AddNewLogic.missingMovieIds(movies, 5))
    }

    @Test
    fun `toAddMoviePayload encodes the form as the web client does`() {
        val candidate = MovieResource(
            title = "Dune",
            year = 2021,
            tmdbId = 438631,
            overview = "A noble house...",
            images = listOf(MediaCover(coverType = MediaCoverTypes.POSTER, remoteUrl = "/abc.jpg")),
            sizeOnDisk = 9L,
            statistics = dev.appolon.homelab.arr.api.radarr.model.MovieStatisticsResource(),
        )
        val payload = AddNewLogic.toAddMoviePayload(
            candidate = candidate,
            rootFolderPath = "/media/films",
            qualityProfileId = 7,
            monitor = MonitorTypes.MOVIE_AND_COLLECTION,
            minimumAvailability = MovieStatusType.ANNOUNCED,
            tags = setOf(3),
            searchForMovie = true,
        )
        assertEquals(0, payload.id)
        assertEquals(true, payload.monitored)
        assertEquals("/media/films", payload.rootFolderPath)
        assertEquals(7, payload.qualityProfileId)
        assertEquals(MovieStatusType.ANNOUNCED, payload.minimumAvailability)
        assertEquals(setOf(3), payload.tags)
        assertEquals(MonitorTypes.MOVIE_AND_COLLECTION, payload.addOptions?.monitor)
        assertEquals(true, payload.addOptions?.searchForMovie)
        assertEquals(438631, payload.tmdbId)
        assertEquals("Dune", payload.title)
        assertNull(payload.statistics)
        assertNull(payload.sizeOnDisk)
    }

    @Test
    fun `lookup and discovery poster urls prefer tmdb remote art`() {
        val movie = MovieResource(
            tmdbId = 1,
            images = listOf(
                MediaCover(coverType = MediaCoverTypes.POSTER, remoteUrl = "/poster.jpg"),
            ),
            remotePoster = "/fallback.jpg",
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/poster.jpg",
            AddNewLogic.lookupPosterUrl(movie),
        )
        val item = ImportListMoviesResource(tmdbId = 2, remotePoster = "/remote.jpg")
        assertEquals(
            "https://image.tmdb.org/t/p/w500/remote.jpg",
            AddNewLogic.discoveryPosterUrl(item),
        )
    }

    @Test
    fun `toCandidate maps discovery metadata without library-only fields`() {
        val item = ImportListMoviesResource(
            title = "Heat",
            tmdbId = 949,
            year = 1995,
            isPopular = true,
            isExcluded = true,
        )
        val candidate = AddNewLogic.toCandidate(item)
        assertEquals("Heat", candidate.title)
        assertEquals(949, candidate.tmdbId)
        assertEquals(1995, candidate.year)
        assertEquals(false, candidate.monitored)
        assertNull(candidate.addOptions)
    }

    @Test
    fun `looksLikeYear accepts plausible years only`() {
        assertTrue(AddNewLogic.looksLikeYear("1999"))
        assertFalse(AddNewLogic.looksLikeYear("1899"))
        assertFalse(AddNewLogic.looksLikeYear("team"))
    }
}
