package dev.appolon.homelab.arr.feature.bazarr.episodes.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Defensive parsing of the schema-less Bazarr payloads into typed models. */
class BazarrSubtitleModelsTest {

    @Test
    fun `episode row parses tracks and missing languages from the data envelope`() {
        val payload = buildJsonObject {
            put("data", buildJsonArray {
                add(buildJsonObject {
                    put("episodeId", 5)
                    put("seriesId", 12)
                    put("seasonNumber", 2)
                    put("episodeNumber", 3)
                    put("title", "Cold Snap")
                    put(
                        "subtitles",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("path", "/tv/2x3.en.srt")
                                    put("language", buildJsonObject {
                                        put("code2", "en")
                                        put("name", "English")
                                    })
                                    put("score", 88.5)
                                    put("hearing_impaired", true)
                                    put("provider", "opensubtitles")
                                },
                            )
                        },
                    )
                    put(
                        "missing_language",
                        buildJsonArray {
                            add(buildJsonObject {
                                put("code2", "de")
                                put("name", "German")
                                put("hi", true)
                            })
                        },
                    )
                })
            })
            put("total", 1)
        }

        val episode = payload.rowsOf().single().toEpisodeSubtitles()!!

        assertEquals(5, episode.episodeId)
        assertEquals("S02E03", episode.code)
        val track = episode.subtitles.single()
        assertEquals("en", track.languageCode2)
        assertEquals("English", track.languageName)
        assertEquals(88.5, track.score!!, 0.001)
        assertTrue(track.hearingImpaired)
        assertFalse(track.forced)
        assertEquals("opensubtitles", track.provider)
        val missing = episode.missingLanguages.single()
        assertEquals("de", missing.code2)
        assertTrue(missing.hearingImpaired)
    }

    @Test
    fun `episode row tolerates string languages and absent optional keys`() {
        val row = buildJsonObject {
            put("episodeId", 7)
            put("language", "ignored")
            put("subtitles", buildJsonArray {
                add(buildJsonObject { put("language", "fr") })
            })
        }

        val episode = row.toEpisodeSubtitles()!!

        assertEquals(7, episode.episodeId)
        assertEquals("", episode.title)
        assertEquals(0, episode.seasonNumber)
        assertEquals("fr", episode.subtitles.single().languageCode2)
        assertTrue(episode.missingLanguages.isEmpty())
    }

    @Test
    fun `rowsOf falls back to the first array member when data is absent`() {
        val payload = buildJsonObject {
            put("rows", buildJsonArray { add(buildJsonObject { put("episodeId", 1) }) })
        }

        assertEquals(1, payload.rowsOf().size)
        assertEquals(0, JsonObject(emptyMap()).rowsOf().size)
    }

    @Test
    fun `episode row without an id is dropped instead of crashing`() {
        val row = buildJsonObject { put("title", "no id") }
        assertNull(row.toEpisodeSubtitles())
    }

    @Test
    fun `movie row parses the radarr id, year, and breakdown`() {
        val payload = buildJsonObject {
            put("data", buildJsonArray {
                add(buildJsonObject {
                    put("radarrId", 42)
                    put("title", "Arrival")
                    put("year", 2016)
                    put("subtitles", buildJsonArray { add(trackRowLike()) })
                    put("missing_language", buildJsonArray { add(languageRowLike("es", "Spanish")) })
                })
            })
        }

        val movie = payload.rowsOf().single().toMovieSubtitles()!!

        assertEquals(42, movie.radarrId)
        assertEquals("Arrival", movie.title)
        assertEquals(2016, movie.year)
        assertEquals(1, movie.subtitles.size)
        assertEquals("es", movie.missingLanguages.single().code2)
    }

    private fun trackRowLike(): JsonObject = buildJsonObject {
        put("path", "/movies/arrival.en.srt")
        put("language", "en")
        put("score", 100)
        put("forced", true)
    }

    private fun languageRowLike(code2: String, name: String): JsonObject = buildJsonObject {
        put("code2", code2)
        put("name", name)
    }

    @Test
    fun `history timestamps parse from epoch seconds and iso strings`() {
        val epochRow = buildJsonObject {
            put("action", "downloaded")
            put("timestamp", 1790000000.0)
            put("episodeId", 3)
        }
        val isoRow = buildJsonObject {
            put("action", "uploaded")
            put("timestamp", "2026-01-31 09:12:00")
            put("radarrId", 9)
        }
        val blankRow = buildJsonObject {
            put("action", "deleted")
            put("episodeId", 4)
        }

        val epoch = epochRow.toHistoryEvent(isMovie = false)!!
        val iso = isoRow.toHistoryEvent(isMovie = true)!!
        val blank = blankRow.toHistoryEvent(isMovie = false)!!

        assertEquals(1790000000_000L, epoch.timestampMillis)
        assertTrue(iso.timestampMillis > 0)
        assertEquals(0, blank.timestampMillis)
    }

    @Test
    fun `language options dedupe by code and skip blanks`() {
        val payload = buildJsonObject {
            put("data", buildJsonArray {
                add(buildJsonObject { put("code2", "en"); put("name", "English") })
                add(buildJsonObject { put("code2", "en"); put("name", "English") })
                add(buildJsonObject { put("name", "No code") })
                add(buildJsonObject { put("code2", "de"); put("name", "German") })
            })
        }

        val options = payload.toLanguageOptions()

        assertEquals(listOf("en", "de"), options.map { it.code2 })
        assertEquals("English", options.first().label)
        assertEquals("German", options.last().label)
    }

    @Test
    fun `provider result parses score, flags, and release info`() {
        val row = buildJsonObject {
            put("provider", "subtitulamos")
            put("subtitle", "abc-1")
            put("language", buildJsonObject { put("code2", "es"); put("name", "Spanish") })
            put("score", 73.0)
            put("forced", true)
            put("original_format", true)
            put("release_info", "show.s02e03.1080p")
            put("upvotes", 4)
        }

        val result = row.toProviderResult()

        assertEquals("subtitulamos", result.provider)
        assertEquals("abc-1", result.subtitleId)
        assertEquals("es", result.languageCode2)
        assertEquals(73.0, result.score!!, 0.001)
        assertTrue(result.forced)
        assertTrue(result.originalFormat)
        assertEquals(4, result.upvotes)
    }

    @Test
    fun `subtitle contents read the first known text member`() {
        val payload = buildJsonObject { put("data", "1\n00:00:01,000 --> 00:00:02,000\nHello") }

        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello", payload.toSubtitleContents())
        assertEquals("", JsonObject(emptyMap()).toSubtitleContents())
    }
}
