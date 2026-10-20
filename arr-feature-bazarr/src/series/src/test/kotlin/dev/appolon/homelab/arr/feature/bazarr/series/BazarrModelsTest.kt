package dev.appolon.homelab.arr.feature.bazarr.series

import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrParsers
import dev.appolon.homelab.arr.feature.bazarr.series.data.SubtitleStatus
import dev.appolon.homelab.arr.feature.bazarr.series.data.SubtitleStatuses
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BazarrModelsTest {

    @Test
    fun seriesStatusCoversEveryCountCombination() {
        assertEquals(SubtitleStatus.NONE_WANTED, SubtitleStatuses.ofSeries(null, 10, 5))
        assertEquals(SubtitleStatus.NONE_WANTED, SubtitleStatuses.ofSeries(0, 10, 5))
        assertEquals(SubtitleStatus.NONE_WANTED, SubtitleStatuses.ofSeries(2, 0, 0))
        assertEquals(SubtitleStatus.COMPLETE, SubtitleStatuses.ofSeries(2, 10, 0))
        assertEquals(SubtitleStatus.PARTIAL, SubtitleStatuses.ofSeries(2, 10, 3))
        assertEquals(SubtitleStatus.MISSING, SubtitleStatuses.ofSeries(2, 10, 10))
        // Out-of-range missing counts clamp instead of inventing a state.
        assertEquals(SubtitleStatus.MISSING, SubtitleStatuses.ofSeries(2, 10, 99))
    }

    @Test
    fun unitStatusDistinguishesPresentAndMissingLanguages() {
        val en = dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLanguage(
            code2 = "eng",
            name = "English",
        )
        assertEquals(SubtitleStatus.NONE_WANTED, SubtitleStatuses.ofUnit(emptyList(), emptyList()))
        assertEquals(SubtitleStatus.COMPLETE, SubtitleStatuses.ofUnit(listOf(en), emptyList()))
        assertEquals(SubtitleStatus.MISSING, SubtitleStatuses.ofUnit(emptyList(), listOf(en)))
        assertEquals(SubtitleStatus.PARTIAL, SubtitleStatuses.ofUnit(listOf(en), listOf(en)))
    }

    @Test
    fun parseSeriesReadsTheSpecUndocumentedShape() {
        val body = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.seriesItem(
                    id = 7,
                    title = "Show A",
                    profileId = 2,
                    fileCount = 12,
                    missingCount = 5,
                    tags = listOf("anime", "hd"),
                    audio = listOf("eng", "jpn"),
                ),
            ),
            "total" to kotlinx.serialization.json.JsonPrimitive(1),
        )
        val series = BazarrParsers.parseSeries(body).single()
        assertEquals(7, series.seriesId)
        assertEquals("Show A", series.title)
        assertEquals("2020", series.year)
        assertEquals(2020, series.yearValue)
        assertEquals(2, series.profileId)
        assertEquals(7, series.completeEpisodeCount)
        assertEquals(listOf("anime", "hd"), series.tags)
        assertEquals(listOf("eng", "jpn"), series.audioLanguages.map { it.code2 })
        assertEquals(SubtitleStatus.PARTIAL, series.status)
        assertEquals("https://img/7.jpg", series.poster)
    }

    @Test
    fun parseMoviesReadsMissingSubtitlesAndTags() {
        val en = BazarrTestJson.language("eng", "English")
        val de = BazarrTestJson.language("deu", "German")
        val body = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.movieItem(
                    id = 3,
                    title = "Movie B",
                    subtitles = listOf(en),
                    missing = listOf(de),
                    tags = listOf("sf"),
                ),
            ),
            "total" to kotlinx.serialization.json.JsonPrimitive(1),
        )
        val movie = BazarrParsers.parseMovies(body).single()
        assertEquals(3, movie.radarrId)
        assertEquals(SubtitleStatus.PARTIAL, movie.status)
        assertEquals(listOf("English"), movie.subtitles.map { it.name })
        assertEquals(listOf("deu"), movie.missingSubtitles.map { it.code2 })
        assertEquals(listOf("sf"), movie.tags)
    }

    @Test
    fun parseEpisodesKeepsSeasonAndEpisodeNumbers() {
        val body = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.episodeItem(
                    seriesId = 5,
                    episodeId = 51,
                    season = 2,
                    episode = 5,
                    title = "Pilot II",
                    subtitles = listOf(BazarrTestJson.language("eng", "English")),
                ),
            ),
        )
        val episode = BazarrParsers.parseEpisodes(body).single()
        assertEquals(51, episode.episodeId)
        assertEquals(5, episode.seriesId)
        assertEquals("S02E05", episode.code)
        assertEquals(SubtitleStatus.COMPLETE, episode.status)
    }

    @Test
    fun parseProfilesReadsWantedLanguagesFromItems() {
        val body = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.profileItem(1, "English only", listOf("eng")),
                BazarrTestJson.profileItem(2, "Multi", listOf("eng", "deu"), cutoff = 1),
                // id fallback plus empty items must survive a nameless row.
                BazarrTestJson.obj("id" to kotlinx.serialization.json.JsonPrimitive(3)),
            ),
        )
        val profiles = BazarrParsers.parseProfiles(body)
        assertEquals(3, profiles.size)
        assertEquals(listOf("eng"), profiles[0].languages)
        assertEquals(1, profiles[1].cutoff)
        assertEquals(3, profiles[2].profileId)
        assertTrue(profiles[2].languages.isEmpty())
        assertEquals("#3", profiles[2].label)
    }

    @Test
    fun parseBlacklistJoinsOnSeriesAndMovieIds() {
        val episodeBody = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.blacklistItem("argenteam", seriesId = 4, title = "Show"),
            ),
        )
        val movieBody = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.blacklistItem("opensubtitles", radarrId = 9, title = "Film"),
            ),
        )
        val episodes = BazarrParsers.parseEpisodeBlacklist(episodeBody)
        val movies = BazarrParsers.parseMovieBlacklist(movieBody)
        assertEquals(4, episodes.single().seriesId)
        assertEquals("argenteam", episodes.single().provider)
        assertEquals(9, movies.single().radarrId)
        assertEquals("opensubtitles", movies.single().provider)
    }

    @Test
    fun parseBadgesReadsFlatMissingCounts() {
        val body = BazarrTestJson.obj(
            "episodes" to kotlinx.serialization.json.JsonPrimitive(12),
            "movies" to kotlinx.serialization.json.JsonPrimitive(3),
            "providers" to kotlinx.serialization.json.JsonPrimitive(1),
            "status" to kotlinx.serialization.json.JsonPrimitive(0),
        )
        val badges = BazarrParsers.parseBadges(body)
        assertEquals(12, badges.episodesMissing)
        assertEquals(3, badges.moviesMissing)
        assertEquals(1, badges.providers)
    }

    @Test
    fun missingKeysNeverFailALoad() {
        assertTrue(BazarrParsers.parseSeries(JsonObject(emptyMap())).isEmpty())
        assertTrue(BazarrParsers.parseMovies(JsonObject(emptyMap())).isEmpty())
        assertTrue(BazarrParsers.parseEpisodes(JsonObject(emptyMap())).isEmpty())
        assertTrue(BazarrParsers.parseProfiles(JsonObject(emptyMap())).isEmpty())
        assertTrue(BazarrParsers.parseEpisodeBlacklist(JsonObject(emptyMap())).isEmpty())
        val badges = BazarrParsers.parseBadges(JsonObject(emptyMap()))
        assertEquals(0, badges.episodesMissing)

        // A row without its id key drops out; the rest of the list loads.
        val partial = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.obj("title" to kotlinx.serialization.json.JsonPrimitive("no id")),
                BazarrTestJson.seriesItem(id = 1, title = "Kept"),
            ),
        )
        val series = BazarrParsers.parseSeries(partial)
        assertEquals(1, series.size)
        assertEquals("Kept", series.single().title)
        assertNull(series.single().overview)
        assertNotNull(series.single().audioLanguages)
    }
}
