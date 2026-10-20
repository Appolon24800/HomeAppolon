package dev.appolon.homelab.arr.feature.bazarr.series

import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryFilters
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMovieSorts
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrParsers
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeriesSorts
import dev.appolon.homelab.arr.feature.bazarr.series.data.SubtitleStatus
import dev.appolon.homelab.arr.feature.bazarr.series.data.presentMovies
import dev.appolon.homelab.arr.feature.bazarr.series.data.presentSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BazarrLibraryFiltersTest {

    private fun series(
        id: Int,
        title: String,
        profileId: Int? = 1,
        fileCount: Int = 10,
        missingCount: Int = 0,
        tags: List<String> = emptyList(),
    ): dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeries =
        BazarrParsers.parseSeries(
            BazarrTestJson.obj(
                "data" to BazarrTestJson.array(
                    BazarrTestJson.seriesItem(id, title, profileId, fileCount, missingCount, tags),
                ),
            ),
        ).single()

    private fun movie(
        id: Int,
        title: String,
        subtitles: List<String> = emptyList(),
        missing: List<String> = emptyList(),
        profileId: Int? = 1,
        tags: List<String> = emptyList(),
        hearingImpaired: Boolean = false,
    ): dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMovie =
        BazarrParsers.parseMovies(
            BazarrTestJson.obj(
                "data" to BazarrTestJson.array(
                    BazarrTestJson.movieItem(
                        id = id,
                        title = title,
                        profileId = profileId,
                        subtitles = subtitles.map { BazarrTestJson.language(it, hi = hearingImpaired) },
                        missing = missing.map { BazarrTestJson.language(it) },
                        tags = tags,
                    ),
                ),
            ),
        ).single()

    private fun profile(
        id: Int,
        name: String,
        languages: List<String>,
    ) = BazarrParsers.parseProfiles(
        BazarrTestJson.obj(
            "data" to BazarrTestJson.array(BazarrTestJson.profileItem(id, name, languages)),
        ),
    ).single()

    private fun blacklist(seriesId: Int) = BazarrParsers.parseEpisodeBlacklist(
        BazarrTestJson.obj(
            "data" to BazarrTestJson.array(BazarrTestJson.blacklistItem("prov", seriesId = seriesId)),
        ),
    ).single()

    private val profiles = listOf(
        profile(1, "Any", listOf("eng", "deu")),
        profile(2, "English only", listOf("eng")),
    )

    @Test
    fun seriesOptionsDeriveTagsStatusesAndJoins() {
        val items = listOf(
            series(1, "A", tags = listOf("hd")),
            series(2, "B", fileCount = 5, missingCount = 5, tags = listOf("sd", "hd")),
        )
        val blacklist = listOf(blacklist(2))
        val options = BazarrLibraryFilters.seriesOptions(items, blacklist, profiles)
        assertEquals(listOf("hd", "sd"), options.tags)
        assertEquals(listOf(SubtitleStatus.COMPLETE, SubtitleStatus.MISSING), options.statuses)
        assertEquals(setOf(2), options.blacklistedIds)
        // Profile 2 wants exactly one language; no library item uses it here.
        assertTrue(options.monolingualIds.isEmpty())
    }

    @Test
    fun subtitleLanguageFilterMatchesSeriesAudio() {
        val items = listOf(series(1, "A"), series(2, "B"))
        val options = BazarrLibraryFilters.seriesOptions(items, emptyList(), profiles)
        val state = FilterState().with(
            BazarrLibraryFilters.SUBTITLES,
            FilterValue.Choice(setOf("eng")),
        )
        val presented = presentSeries(items, options, state, BazarrSeriesSorts.TITLE, false, 60)
        assertEquals(listOf(1, 2), presented.all.map { it.seriesId })
        val german = state.with(BazarrLibraryFilters.SUBTITLES, FilterValue.Choice(setOf("deu")))
        val none = presentSeries(items, options, german, BazarrSeriesSorts.TITLE, false, 60)
        assertTrue(none.all.isEmpty())
    }

    @Test
    fun seriesSpecsOmitHearingImpairedAndMoviesIncludeIt() {
        val options = BazarrLibraryFilters.seriesOptions(
            listOf(series(1, "A")), emptyList(), profiles,
        )
        val seriesIds = BazarrLibraryFilters.seriesSpecs(options).map { it.id }
        assertFalse(BazarrLibraryFilters.HEARING_IMPAIRED in seriesIds)

        val movieOptions = BazarrLibraryFilters.movieOptions(
            listOf(movie(1, "M")), emptyList(), profiles,
        )
        val movieIds = BazarrLibraryFilters.movieSpecs(movieOptions).map { it.id }
        assertTrue(BazarrLibraryFilters.HEARING_IMPAIRED in movieIds)
    }

    @Test
    fun hearingImpairedFilterMatchesMovieSubtitlesOnly() {
        val withHi = movie(1, "A", subtitles = listOf("eng"), hearingImpaired = true)
        val withoutHi = movie(2, "B", subtitles = listOf("eng"))
        val options = BazarrLibraryFilters.movieOptions(listOf(withHi, withoutHi), emptyList(), emptyList())

        val on = presentMovies(
            listOf(withHi, withoutHi), options,
            FilterState().with(BazarrLibraryFilters.HEARING_IMPAIRED, FilterValue.Toggle(true)),
            BazarrMovieSorts.TITLE, false, 60,
        )
        assertEquals(listOf(1), on.all.map { it.radarrId })

        val off = presentMovies(
            listOf(withHi, withoutHi), options,
            FilterState().with(BazarrLibraryFilters.HEARING_IMPAIRED, FilterValue.Toggle(false)),
            BazarrMovieSorts.TITLE, false, 60,
        )
        assertEquals(listOf(2), off.all.map { it.radarrId })
    }

    @Test
    fun monolingualFilterJoinsOnSingleLanguageProfiles() {
        val items = listOf(
            movie(1, "A", profileId = 1),
            movie(2, "B", profileId = 2),
            movie(3, "C", profileId = null),
        )
        val options = BazarrLibraryFilters.movieOptions(items, emptyList(), profiles)
        val state = FilterState().with(
            BazarrLibraryFilters.MONOLINGUAL,
            FilterValue.Toggle(true),
        )
        val presented = presentMovies(items, options, state, BazarrMovieSorts.TITLE, false, 60)
        assertEquals(listOf(2), presented.all.map { it.radarrId })
    }

    @Test
    fun tagChoiceOresAcrossSelectedTags() {
        val a = movie(1, "A", tags = listOf("hd"))
        val b = movie(2, "B", tags = listOf("sd"))
        val c = movie(3, "C", tags = listOf("4k"))
        val options = BazarrLibraryFilters.movieOptions(listOf(a, b, c), emptyList(), emptyList())
        val state = FilterState().with(
            BazarrLibraryFilters.TAGS,
            FilterValue.Choice(setOf("hd", "4k")),
        )
        val presented = presentMovies(listOf(a, b, c), options, state, BazarrMovieSorts.TITLE, false, 60)
        assertEquals(listOf(1, 3), presented.all.map { it.radarrId })
    }

    @Test
    fun blacklistFilterJoinsOnBlacklistEntries() {
        val a = series(1, "A")
        val b = series(2, "B")
        val blacklist = listOf(blacklist(2))
        val options = BazarrLibraryFilters.seriesOptions(listOf(a, b), blacklist, emptyList())
        val state = FilterState().with(BazarrLibraryFilters.BLACKLIST, FilterValue.Toggle(true))
        val presented = presentSeries(listOf(a, b), options, state, BazarrSeriesSorts.TITLE, false, 60)
        assertEquals(listOf(2), presented.all.map { it.seriesId })
    }

    @Test
    fun statusChoiceFiltersByStatusName() {
        val complete = movie(1, "A", subtitles = listOf("eng"))
        val missing = movie(2, "B", missing = listOf("eng"))
        val items = listOf(complete, missing)
        val options = BazarrLibraryFilters.movieOptions(items, emptyList(), emptyList())
        val state = FilterState().with(
            BazarrLibraryFilters.STATUS,
            FilterValue.Choice(setOf(SubtitleStatus.MISSING.name)),
        )
        val presented = presentMovies(items, options, state, BazarrMovieSorts.TITLE, false, 60)
        assertEquals(listOf(2), presented.all.map { it.radarrId })
    }

    @Test
    fun sortsOrderByTitleYearAndMissing() {
        val items = listOf(
            series(1, "Beta", fileCount = 10, missingCount = 1),
            series(2, "Alpha", fileCount = 8, missingCount = 4),
        )
        val options = BazarrLibraryFilters.seriesOptions(items, emptyList(), emptyList())
        val byTitle = presentSeries(
            items, options, FilterState(), BazarrSeriesSorts.TITLE, false, 60,
        )
        assertEquals(listOf("Alpha", "Beta"), byTitle.all.map { it.title })
        val byMissingDesc = presentSeries(
            items, options, FilterState(), BazarrSeriesSorts.MISSING, true, 60,
        )
        assertEquals(listOf(2, 1), byMissingDesc.all.map { it.seriesId })
    }

    @Test
    fun presentationWindowsTheSortedList() {
        val items = (1..120).map { series(it, "S%03d".format(it)) }
        val options = BazarrLibraryFilters.seriesOptions(items, emptyList(), emptyList())
        val first = presentSeries(
            items, options, FilterState(), BazarrSeriesSorts.TITLE, false, 60,
        )
        assertEquals(60, first.visible.size)
        assertFalse(first.endReached)
        val rest = presentSeries(
            items, options, FilterState(), BazarrSeriesSorts.TITLE, false, 120,
        )
        assertEquals(120, rest.visible.size)
        assertTrue(rest.endReached)
    }
}
