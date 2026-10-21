package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePresentersTest {

    private fun release(
        guid: String,
        fullSeason: Boolean = false,
        seasonNumber: Int = 2,
        episodeNumbers: List<Int>? = null,
        mappedEpisodeNumbers: List<Int>? = null,
        score: Int = 0,
        releaseWeight: Int = 0,
        seeders: Int? = null,
        ageHours: Double = 0.0,
        size: Long = 0L,
        approved: Boolean = false,
    ) = ReleaseResource(
        guid = guid,
        title = "Some.Series.S02E05.1080p.$guid",
        fullSeason = fullSeason,
        seasonNumber = seasonNumber,
        episodeNumbers = episodeNumbers,
        mappedEpisodeNumbers = mappedEpisodeNumbers,
        customFormatScore = score,
        releaseWeight = releaseWeight,
        seeders = seeders,
        leechers = seeders?.let { it / 2 },
        protocol = if (seeders == null) DownloadProtocol.USENET else DownloadProtocol.TORRENT,
        ageHours = ageHours,
        ageMinutes = ageHours * 60,
        size = size,
        approved = approved,
        indexer = "indexer-a",
        quality = QualityModel(quality = Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
        customFormats = listOf(CustomFormatResource(id = 1, name = "HQ")),
        rejections = listOf("Wrong quality"),
    )

    @Test
    fun `releaseRow maps every column the list shows`() {
        val row = releaseRow(
            release("g1", seeders = 12, score = 30, approved = true, episodeNumbers = listOf(5)),
        )

        assertEquals("Some.Series.S02E05.1080p.g1", row.title)
        assertEquals("indexer-a", row.indexer)
        assertEquals("WEB-DL 1080p", row.qualityName)
        assertEquals(30, row.qualityScore)
        assertEquals("HQ", row.customFormats)
        assertFalse(row.seasonPack)
        assertEquals("S02E05", row.episodeLabel)
        assertEquals("English", row.languageNames)
        assertEquals(12, row.seeders)
        assertTrue(row.approved)
        assertTrue(row.hasRejections)
    }

    @Test
    fun `episodeLabelOf spans multi episode releases and drops season packs`() {
        assertEquals("S02E05", episodeLabelOf(release("g1", episodeNumbers = listOf(5))))
        assertEquals(
            "S02E05-E07",
            episodeLabelOf(release("g2", episodeNumbers = listOf(5, 6, 7))),
        )
        assertNull(episodeLabelOf(release("g3", fullSeason = true)))
        assertNull(episodeLabelOf(release("g4", seasonNumber = 0, episodeNumbers = listOf(5))))
    }

    @Test
    fun `mapped episode numbers win over scene numbering`() {
        val mapped = release("g1", episodeNumbers = listOf(14), mappedEpisodeNumbers = listOf(5))

        assertEquals("S02E05", episodeLabelOf(mapped))
    }

    @Test
    fun `season pack and episode releases split by the group filter`() {
        val rows = listOf(
            releaseRow(release("pack", fullSeason = true)),
            releaseRow(release("ep", episodeNumbers = listOf(5))),
        )

        assertEquals(2, filterReleaseRows(rows, ReleaseGroupFilter.ALL).size)
        assertEquals(
            listOf("pack"),
            filterReleaseRows(rows, ReleaseGroupFilter.SEASON_PACKS).map { it.release.guid },
        )
        assertEquals(
            listOf("ep"),
            filterReleaseRows(rows, ReleaseGroupFilter.EPISODES).map { it.release.guid },
        )
    }

    @Test
    fun `sorting covers every key with null seeders sinking`() {
        val low = releaseRow(release("low", score = -5, seeders = 40))
        val high = releaseRow(release("high", score = 20, seeders = 3))
        val usenet = releaseRow(release("usenet", score = 1, seeders = null))

        assertEquals(
            listOf("high", "usenet", "low"),
            sortReleaseRows(listOf(low, high, usenet), ReleaseSort.QUALITY_SCORE)
                .map { it.release.guid },
        )
        assertEquals(
            listOf("low", "high", "usenet"),
            sortReleaseRows(listOf(low, high, usenet), ReleaseSort.SEEDERS).map { it.release.guid },
        )
        assertEquals(
            listOf("new", "old"),
            sortReleaseRows(
                listOf(
                    releaseRow(release("old", ageHours = 48.0, seeders = 5)),
                    releaseRow(release("new", ageHours = 1.0, seeders = 5)),
                ),
                ReleaseSort.AGE,
            ).map { it.release.guid },
        )
        assertEquals(
            listOf("big", "small"),
            sortReleaseRows(
                listOf(
                    releaseRow(release("small", size = 1L)),
                    releaseRow(release("big", size = 2L)),
                ),
                ReleaseSort.SIZE,
            ).map { it.release.guid },
        )
    }

    @Test
    fun `quality score ties break by release weight`() {
        val rows = listOf(
            releaseRow(release("light", score = 10, releaseWeight = 1)),
            releaseRow(release("heavy", score = 10, releaseWeight = 9)),
        )

        assertEquals(
            listOf("heavy", "light"),
            sortReleaseRows(rows, ReleaseSort.QUALITY_SCORE).map { it.release.guid },
        )
    }

    @Test
    fun `applyGrabOverrides replaces quality and language and keeps the rest`() {
        val body = release("g1", approved = true)

        val overridden = applyGrabOverrides(
            body,
            Quality(id = 9, name = "Bluray-1080p"),
            Language(id = 3, name = "German"),
        )

        assertEquals(9, overridden.quality.quality.id)
        assertEquals(listOf(3), overridden.languages?.map { it.id })
        assertEquals(body.guid, overridden.guid)
        assertEquals(body.quality.revision, overridden.quality.revision)
    }

    @Test
    fun `applyGrabOverrides without choices keeps the parsed values`() {
        val body = release("g1")

        val untouched = applyGrabOverrides(body, null, null)

        assertEquals(body.quality, untouched.quality)
        assertEquals(body.languages, untouched.languages)
    }
}
