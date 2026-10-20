package dev.appolon.homelab.arr.feature.sonarr.wanted.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WantedPresentationTest {

    private fun episode(
        id: Int,
        seriesId: Int,
        seriesTitle: String,
        season: Int,
        number: Int,
        monitored: Boolean = true,
    ) = EpisodeResource(
        id = id,
        seriesId = seriesId,
        seasonNumber = season,
        episodeNumber = number,
        title = "Episode $number",
        monitored = monitored,
        series = SeriesResource(
            id = seriesId,
            title = seriesTitle,
            sortTitle = seriesTitle.lowercase(),
        ),
    )

    private val episodes = listOf(
        episode(1, 20, "Beta", season = 2, number = 1, monitored = true),
        episode(2, 10, "Alpha", season = 1, number = 2, monitored = false),
        episode(3, 10, "Alpha", season = 1, number = 1, monitored = true),
        episode(4, 30, "Gamma", season = 3, number = 1, monitored = true),
    )

    @Test
    fun `unfiltered presentation sorts by series then season then episode`() {
        val options = WantedFilters.optionsFrom(episodes)
        val presented = presentWanted(episodes, options, FilterState())
        assertEquals(listOf(3, 2, 1, 4), presented.all.map { it.id })
    }

    @Test
    fun `series without titles sort last but stay stable`() {
        val anonymous = EpisodeResource(
            id = 9,
            seriesId = 77,
            seasonNumber = 1,
            episodeNumber = 1,
            series = SeriesResource(id = 77),
        )
        val options = WantedFilters.optionsFrom(episodes + anonymous)
        val presented = presentWanted(episodes + anonymous, options, FilterState())
        assertEquals(9, presented.all.last().id)
    }

    @Test
    fun `monitored toggle keeps only the chosen side`() {
        val options = WantedFilters.optionsFrom(episodes)
        val on = presentWanted(
            episodes, options,
            FilterState().with(WantedFilters.MONITORED, FilterValue.Toggle(true)),
        )
        assertEquals(listOf(3, 1, 4), on.all.map { it.id })
        val off = presentWanted(
            episodes, options,
            FilterState().with(WantedFilters.MONITORED, FilterValue.Toggle(false)),
        )
        assertEquals(listOf(2), off.all.map { it.id })
    }

    @Test
    fun `season choice filters by season number`() {
        val options = WantedFilters.optionsFrom(episodes)
        val filtered = presentWanted(
            episodes, options,
            FilterState().with(WantedFilters.SEASON, FilterValue.Choice(setOf("1"))),
        )
        assertEquals(listOf(3, 2), filtered.all.map { it.id })
    }

    @Test
    fun `season and monitored filters combine with and`() {
        val options = WantedFilters.optionsFrom(episodes)
        val filtered = presentWanted(
            episodes, options,
            FilterState()
                .with(WantedFilters.SEASON, FilterValue.Choice(setOf("1")))
                .with(WantedFilters.MONITORED, FilterValue.Toggle(true)),
        )
        assertEquals(listOf(3), filtered.all.map { it.id })
    }

    @Test
    fun `options carry the seasons seen in the list`() {
        val options = WantedFilters.optionsFrom(episodes)
        assertEquals(listOf(1, 2, 3), options.seasons)
    }

    @Test
    fun `specs and definitions stay id aligned`() {
        val options = WantedFilters.optionsFrom(episodes)
        val specs = WantedFilters.specs(options)
        val definitions = WantedFilters.definitions(options)
        assertEquals(specs.map { it.id }, definitions.map { it.spec.id })
        assertTrue(
            specs.first { it.id == WantedFilters.SEASON } is
                dev.appolon.homelab.arr.core.FilterSpec.Choice,
        )
    }

    @Test
    fun `episode code renders SxxEyy`() {
        assertEquals("S01E05", episodeCode(episode(1, 1, "A", 1, 5)))
        assertEquals("S10E12", episodeCode(episode(1, 1, "A", 10, 12)))
    }

    @Test
    fun `series title falls back to the series id`() {
        assertEquals("Alpha", seriesTitleOf(episode(1, 10, "Alpha", 1, 1)))
        val anonymous = EpisodeResource(seriesId = 42, series = SeriesResource(id = 42))
        assertEquals("#42", seriesTitleOf(anonymous))
    }

    @Test
    fun `tab meta reports end reached against cached rows`() {
        val meta = WantedTabMeta(totalRecords = 100, pagesLoaded = 2)
        assertFalse(meta.endReached(99))
        assertTrue(meta.endReached(100))
    }
}
