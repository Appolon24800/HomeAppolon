package dev.appolon.homelab.arr.feature.radarr.wanted.data

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WantedPresentationTest {

    private fun movie(
        id: Int,
        title: String,
        monitored: Boolean = true,
        profile: Int = 1,
        year: Int = 2000,
    ) = MovieResource(
        id = id,
        title = title,
        sortTitle = title.lowercase(),
        monitored = monitored,
        qualityProfileId = profile,
        year = year,
    )

    private val movies = listOf(
        movie(1, "Beta", monitored = true, profile = 1),
        movie(2, "Alpha", monitored = false, profile = 2),
        movie(3, "Gamma", monitored = true, profile = 2),
    )

    @Test
    fun `unfiltered presentation sorts by title`() {
        val options = WantedFilters.optionsFrom(movies, knownProfiles = listOf(1, 2, 3))
        val presented = presentWanted(movies, options, FilterState())
        assertEquals(listOf("Alpha", "Beta", "Gamma"), presented.all.map { it.title })
    }

    @Test
    fun `monitored toggle keeps only the chosen side`() {
        val options = WantedFilters.optionsFrom(movies, knownProfiles = emptyList())
        val on = presentWanted(
            movies, options,
            FilterState().with(WantedFilters.MONITORED, FilterValue.Toggle(true)),
        )
        assertEquals(listOf("Beta", "Gamma"), on.all.map { it.title })
        val off = presentWanted(
            movies, options,
            FilterState().with(WantedFilters.MONITORED, FilterValue.Toggle(false)),
        )
        assertEquals(listOf("Alpha"), off.all.map { it.title })
    }

    @Test
    fun `quality choice filters by profile id`() {
        val options = WantedFilters.optionsFrom(movies, knownProfiles = emptyList())
        val filtered = presentWanted(
            movies, options,
            FilterState().with(
                WantedFilters.QUALITY,
                FilterValue.Choice(setOf("2")),
            ),
        )
        assertEquals(listOf("Alpha", "Gamma"), filtered.all.map { it.title })
    }

    @Test
    fun `monitored and quality filters combine with and`() {
        val options = WantedFilters.optionsFrom(movies, knownProfiles = emptyList())
        val filtered = presentWanted(
            movies, options,
            FilterState()
                .with(WantedFilters.MONITORED, FilterValue.Toggle(true))
                .with(WantedFilters.QUALITY, FilterValue.Choice(setOf("2"))),
        )
        assertEquals(listOf("Gamma"), filtered.all.map { it.title })
    }

    @Test
    fun `options merge seen profiles with known ones`() {
        val options = WantedFilters.optionsFrom(movies, knownProfiles = listOf(3, 9, 1))
        assertEquals(listOf(1, 2, 3, 9), options.qualityProfileIds)
    }

    @Test
    fun `specs and definitions stay id aligned`() {
        val options = WantedFilters.optionsFrom(movies, knownProfiles = emptyList())
        val specs = WantedFilters.specs(options)
        val definitions = WantedFilters.definitions(options)
        assertEquals(specs.map { it.id }, definitions.map { it.spec.id })
        assertTrue(
            specs.first { it.id == WantedFilters.QUALITY } is
                dev.appolon.homelab.arr.core.FilterSpec.Choice,
        )
    }

    @Test
    fun `secondary sort keys break title ties`() {
        val options = WantedFilters.optionsFrom(emptyList(), knownProfiles = emptyList())
        val tied = listOf(
            movie(5, "Same", year = 2010),
            movie(4, "Same", year = 2001),
            movie(6, "Same", year = 1999),
        )
        val presented = presentWanted(tied, options, FilterState())
        assertEquals(listOf(6, 4, 5), presented.all.map { it.id })
    }

    @Test
    fun `tab meta reports end reached against cached rows`() {
        val meta = WantedTabMeta(totalRecords = 100, pagesLoaded = 2)
        assertFalse(meta.endReached(99))
        assertTrue(meta.endReached(100))
    }
}
