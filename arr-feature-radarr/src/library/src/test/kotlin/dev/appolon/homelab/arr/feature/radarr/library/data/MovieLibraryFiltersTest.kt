package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieLibraryFiltersTest {

    private fun movie(
        id: Int,
        title: String = "Movie $id",
        year: Int = 2000 + id,
        monitored: Boolean = true,
        hasFile: Boolean = true,
        quality: Int = 1,
        genres: List<String> = emptyList(),
        tags: Set<Int> = emptySet(),
        folder: String? = "/movies",
        size: Long = 1_000L,
    ) = MovieResource(
        id = id,
        title = title,
        sortTitle = title.lowercase(),
        year = year,
        monitored = monitored,
        hasFile = hasFile,
        qualityProfileId = quality,
        genres = genres,
        tags = tags,
        folder = folder,
        statistics = dev.appolon.homelab.arr.api.radarr.model.MovieStatisticsResource(
            movieFileCount = if (hasFile) 1 else 0,
            sizeOnDisk = if (hasFile) size else 0L,
        ),
    )

    private val library = listOf(
        movie(
            1,
            monitored = true,
            hasFile = true,
            quality = 1,
            genres = listOf("Drama"),
            tags = setOf(10),
        ),
        movie(
            2,
            monitored = true,
            hasFile = false,
            quality = 2,
            genres = listOf("Comedy"),
            tags = setOf(11),
        ),
        movie(
            3,
            monitored = false,
            hasFile = true,
            quality = 1,
            genres = listOf("Drama"),
            year = 1999,
        ),
        movie(
            4,
            monitored = true,
            hasFile = false,
            quality = 3,
            genres = listOf(),
            folder = "/other",
        ),
    )

    private val options = MovieLibraryFilters.optionsFrom(library, nowYear = 2026)

    @Test
    fun `options derive from the library`() {
        assertEquals(listOf("Comedy", "Drama"), options.genres)
        assertEquals(listOf("/movies", "/other"), options.folders)
        assertEquals(listOf(10, 11), options.tagIds)
        assertEquals(listOf(1, 2, 3), options.qualityProfileIds)
        assertEquals(1999.0, options.minYear, 0.0)
        assertEquals(2004.0, options.maxYear, 0.0)
    }

    @Test
    fun `monitored toggle narrows both ways`() {
        val definitions = MovieLibraryFilters.definitions(options)
        val on = FilterState().with(MovieLibraryFilters.MONITORED, FilterValue.Toggle(true))
        val off = FilterState().with(MovieLibraryFilters.MONITORED, FilterValue.Toggle(false))
        assertEquals(listOf(1, 2, 4), applyFilters(library, definitions, on).map { it.id })
        assertEquals(listOf(3), applyFilters(library, definitions, off).map { it.id })
    }

    @Test
    fun `missing toggle keeps only monitored movies without a file`() {
        val state = FilterState().with(MovieLibraryFilters.MISSING, FilterValue.Toggle(true))
        val result = applyFilters(library, MovieLibraryFilters.definitions(options), state)
        assertEquals(listOf(2, 4), result.map { it.id })
    }

    @Test
    fun `quality choice ors inside the selection`() {
        val state = FilterState().with(
            MovieLibraryFilters.QUALITY,
            FilterValue.Choice(setOf("1", "3")),
        )
        val result = applyFilters(library, MovieLibraryFilters.definitions(options), state)
        assertEquals(listOf(1, 3, 4), result.map { it.id })
    }

    @Test
    fun `genre choice intersects`() {
        val state = FilterState()
            .with(MovieLibraryFilters.GENRE, FilterValue.Choice(setOf("Drama")))
        val result = applyFilters(library, MovieLibraryFilters.definitions(options), state)
        assertEquals(listOf(1, 3), result.map { it.id })
    }

    @Test
    fun `year range bounds and skips unknown years`() {
        val unknown = movie(9, year = 0)
        val all = library + unknown
        val opts = MovieLibraryFilters.optionsFrom(all, nowYear = 2026)
        val state = FilterState().with(
            MovieLibraryFilters.YEAR,
            FilterValue.Range(1999.0, 2002.0),
        )
        val result = applyFilters(all, MovieLibraryFilters.definitions(opts), state)
        assertTrue(result.all { it.year in 1999..2002 })
        assertFalse(result.any { it.id == 9 })
    }

    @Test
    fun `folder choice matches the resolved folder`() {
        val state = FilterState()
            .with(MovieLibraryFilters.FOLDER, FilterValue.Choice(setOf("/other")))
        val result = applyFilters(library, MovieLibraryFilters.definitions(options), state)
        assertEquals(listOf(4), result.map { it.id })
    }

    @Test
    fun `tags choice matches tag ids`() {
        val state = FilterState().with(MovieLibraryFilters.TAGS, FilterValue.Choice(setOf("11")))
        val result = applyFilters(library, MovieLibraryFilters.definitions(options), state)
        assertEquals(listOf(2), result.map { it.id })
    }

    @Test
    fun `filters and across each other`() {
        val state = FilterState()
            .with(MovieLibraryFilters.MONITORED, FilterValue.Toggle(true))
            .with(MovieLibraryFilters.MISSING, FilterValue.Toggle(true))
        val result = applyFilters(library, MovieLibraryFilters.definitions(options), state)
        assertEquals(listOf(2, 4), result.map { it.id })
    }

    @Test
    fun `inactive filters return the input untouched`() {
        val state = FilterState()
            .with(MovieLibraryFilters.MONITORED, FilterValue.Toggle(null))
            .with(MovieLibraryFilters.QUALITY, FilterValue.Choice(emptySet()))
        val definitions = MovieLibraryFilters.definitions(options)
        assertEquals(library, applyFilters(library, definitions, state))
    }

    @Test
    fun `sort by title uses sort title ascending`() {
        val shuffled = listOf(library[2], library[0], library[3], library[1])
        val sorted = sortItems(shuffled, MovieLibrarySorts.spec(MovieLibrarySorts.TITLE))
        assertEquals(listOf("Movie 1", "Movie 2", "Movie 3", "Movie 4"), sorted.map { it.title })
    }

    @Test
    fun `sort by size places missing files last`() {
        val sorted = sortItems(library, MovieLibrarySorts.spec(MovieLibrarySorts.SIZE))
        assertEquals(0L, sorted.last().statistics?.sizeOnDisk)
        assertEquals(4, sorted.last().id)
    }

    @Test
    fun `presentation windows the sorted list and reports the end`() {
        val state = FilterState()
        val first = presentMovies(library, options, state, MovieLibrarySorts.TITLE, false, 3)
        assertEquals(3, first.visible.size)
        assertFalse(first.endReached)
        val rest = presentMovies(library, options, state, MovieLibrarySorts.TITLE, false, 60)
        assertEquals(4, rest.visible.size)
        assertTrue(rest.endReached)
        assertEquals(4, rest.all.size)
    }
}
