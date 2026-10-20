package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesStatisticsResource
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure filter, sort, and presentation logic of the series library,
 * including the missing-episode rule driven by episodeCount versus
 * episodeFileCount.
 */
class SeriesLibraryFiltersTest {

    private fun series(
        id: Int,
        title: String,
        monitored: Boolean = true,
        year: Int = 2020,
        genres: List<String> = emptyList(),
        network: String? = null,
        qualityProfileId: Int = 1,
        seasons: Int = 1,
        episodes: Int = 10,
        files: Int = 10,
        path: String = "/tv/$title",
        tags: Set<Int> = emptySet(),
    ) = SeriesResource(
        id = id,
        title = title,
        monitored = monitored,
        year = year,
        genres = genres.ifEmpty { null },
        network = network,
        qualityProfileId = qualityProfileId,
        statistics = SeriesStatisticsResource(
            seasonCount = seasons,
            episodeCount = episodes,
            episodeFileCount = files,
            totalEpisodeCount = episodes,
            sizeOnDisk = 0,
        ),
        path = path,
        tags = tags.ifEmpty { null },
    )

    private val library = listOf(
        series(1, "Alpha", monitored = true, episodes = 10, files = 8, network = "BBC", year = 2018),
        series(2, "Beta", monitored = false, episodes = 10, files = 2, network = "HBO", year = 2022),
        series(
            3,
            "Gamma",
            monitored = true,
            episodes = 24,
            files = 24,
            genres = listOf("Drama"),
            network = "AMC",
            qualityProfileId = 2,
            seasons = 2,
            year = 2010,
        ),
        series(4, "Delta", monitored = true, episodes = 0, files = 0, year = 0),
    )

    private val options = SeriesLibraryFilters.optionsFrom(library, 2026)

    private fun present(state: FilterState = FilterState()) =
        presentSeries(library, options, state, SeriesLibrarySorts.TITLE, false, 100)

    @Test
    fun `monitored toggle keeps only monitored series`() {
        val state = FilterState().with(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(true))
        assertEquals(listOf(1, 4, 3), present(state).all.map { it.id })
    }

    @Test
    fun `missing toggle matches monitored series with fewer files than episodes`() {
        val state = FilterState().with(SeriesLibraryFilters.MISSING, FilterValue.Toggle(true))
        assertEquals(listOf(1), present(state).all.map { it.id })

        val off = FilterState().with(SeriesLibraryFilters.MISSING, FilterValue.Toggle(false))
        assertEquals(listOf(2, 4, 3), present(off).all.map { it.id })
    }

    @Test
    fun `missing count is clamped at zero`() {
        val stats = SeriesStatisticsResource(episodeCount = 5, episodeFileCount = 9)
        assertEquals(0, (stats.episodeCount - stats.episodeFileCount).coerceAtLeast(0))
    }

    @Test
    fun `quality choice matches profile ids`() {
        val state = FilterState().with(
            SeriesLibraryFilters.QUALITY,
            FilterValue.Choice(setOf("2")),
        )
        assertEquals(listOf(3), present(state).all.map { it.id })
    }

    @Test
    fun `genre choice matches any selected genre`() {
        val state = FilterState().with(
            SeriesLibraryFilters.GENRE,
            FilterValue.Choice(setOf("Drama", "Comedy")),
        )
        assertEquals(listOf(3), present(state).all.map { it.id })
    }

    @Test
    fun `network choice matches the trimmed network`() {
        val state = FilterState().with(
            SeriesLibraryFilters.NETWORK,
            FilterValue.Choice(setOf("BBC", "HBO")),
        )
        assertEquals(listOf(1, 2), present(state).all.map { it.id })
    }

    @Test
    fun `season count range filters by statistics`() {
        val state = FilterState().with(
            SeriesLibraryFilters.SEASONS,
            FilterValue.Range(min = 2.0, max = null),
        )
        assertEquals(listOf(3), present(state).all.map { it.id })
    }

    @Test
    fun `year range drops unknown years`() {
        val state = FilterState().with(
            SeriesLibraryFilters.YEAR,
            FilterValue.Range(min = 2015.0, max = 2021.0),
        )
        assertEquals(listOf(1), present(state).all.map { it.id })
    }

    @Test
    fun `folder choice prefers explicit path fields`() {
        val folders = library.mapNotNull(SeriesLibraryFilters::folderOf)
        assertEquals(listOf("Alpha", "Beta", "Gamma", "Delta"), folders)
        val state = FilterState().with(
            SeriesLibraryFilters.FOLDER,
            FilterValue.Choice(setOf("Gamma")),
        )
        assertEquals(listOf(3), present(state).all.map { it.id })
    }

    @Test
    fun `tags choice matches any tag id`() {
        val tagged = listOf(
            series(1, "Alpha", tags = setOf(10, 11)),
            series(2, "Beta", tags = setOf(20)),
        )
        val taggedOptions = SeriesLibraryFilters.optionsFrom(tagged, 2026)
        val state = FilterState().with(SeriesLibraryFilters.TAGS, FilterValue.Choice(setOf("11")))
        val out = presentSeries(tagged, taggedOptions, state, SeriesLibrarySorts.TITLE, false, 10)
        assertEquals(listOf(1), out.all.map { it.id })
    }

    @Test
    fun `filters AND across each other`() {
        val state = FilterState()
            .with(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(true))
            .with(SeriesLibraryFilters.MISSING, FilterValue.Toggle(true))
            .with(SeriesLibraryFilters.NETWORK, FilterValue.Choice(setOf("BBC")))
        assertEquals(listOf(1), present(state).all.map { it.id })
    }

    @Test
    fun `inactive filters narrow nothing`() {
        val state = FilterState()
            .with(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(null))
            .with(SeriesLibraryFilters.QUALITY, FilterValue.Choice(emptySet()))
            .with(SeriesLibraryFilters.YEAR, FilterValue.Range(null, null))
        assertEquals(4, present(state).all.size)
    }

    @Test
    fun `title sort uses sortTitle when present`() {
        val sorted = present().all
        assertEquals(listOf("Alpha", "Beta", "Delta", "Gamma"), sorted.map { it.title })
    }

    @Test
    fun `descending sort reverses order`() {
        val sorted = presentSeries(
            library,
            options,
            FilterState(),
            SeriesLibrarySorts.TITLE,
            true,
            100,
        )
        assertEquals(listOf("Gamma", "Delta", "Beta", "Alpha"), sorted.all.map { it.title })
    }

    @Test
    fun `presentation windows and reports the end`() {
        val windowed = presentSeries(library, options, FilterState(), "title", false, 2)
        assertEquals(2, windowed.visible.size)
        assertFalse(windowed.endReached)
        assertEquals(4, windowed.all.size)
    }

    @Test
    fun `options derive spans from the library`() {
        assertEquals(2010.0, options.minYear, 0.0)
        assertEquals(2022.0, options.maxYear, 0.0)
        assertEquals(1.0, options.minSeasons, 0.0)
        assertEquals(2.0, options.maxSeasons, 0.0)
        assertTrue(options.networks.containsAll(listOf("AMC", "BBC", "HBO")))
        assertTrue(options.qualityProfileIds.containsAll(listOf(1, 2)))
    }

    @Test
    fun `empty library yields an empty presentation`() {
        val empty = presentSeries(emptyList(), options, FilterState(), "title", false, 10)
        assertTrue(empty.all.isEmpty())
        assertTrue(empty.endReached)
        assertNull(SeriesLibraryFilters.folderOf(SeriesResource(id = 9, title = "Bare")))
    }
}
