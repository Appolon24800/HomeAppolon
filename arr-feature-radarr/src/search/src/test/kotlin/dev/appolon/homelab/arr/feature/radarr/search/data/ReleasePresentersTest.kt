package dev.appolon.homelab.arr.feature.radarr.search.data

import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.api.radarr.model.Revision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePresentersTest {

    private fun row(
        guid: String,
        score: Int = 0,
        seeders: Int? = null,
        ageHours: Double = 0.0,
        size: Long = 0L,
        weight: Int = 0,
    ) = releaseRow(
        RadarrReleaseResource(
            guid = guid,
            title = "title-$guid",
            ageHours = ageHours,
            ageMinutes = ageHours * 60,
            size = size,
            seeders = seeders,
            releaseWeight = weight,
            customFormatScore = score,
        ),
    )

    @Test
    fun `quality score sort is descending with release weight breaking ties`() {
        val rows = listOf(
            row("low", score = -10, weight = 5),
            row("high", score = 50, weight = 1),
            row("tie-a", score = 50, weight = 9),
            row("tie-b", score = 50, weight = 2),
        )

        val sorted = sortReleaseRows(rows, ReleaseSort.QUALITY_SCORE)

        assertEquals(
            listOf("tie-a", "tie-b", "high", "low"),
            sorted.map { it.release.guid },
        )
    }

    @Test
    fun `seeders sort puts torrent rows first and rows without seeders last`() {
        val rows = listOf(
            row("none"),
            row("usenet", seeders = null),
            row("ten", seeders = 10),
            row("hundred", seeders = 100),
        )

        val sorted = sortReleaseRows(rows, ReleaseSort.SEEDERS)

        assertEquals(
            listOf("hundred", "ten", "none", "usenet"),
            sorted.map { it.release.guid },
        )
    }

    @Test
    fun `age sort is youngest first`() {
        val rows = listOf(
            row("old", ageHours = 72.0),
            row("new", ageHours = 0.5),
            row("mid", ageHours = 24.0),
        )

        val sorted = sortReleaseRows(rows, ReleaseSort.AGE)

        assertEquals(listOf("new", "mid", "old"), sorted.map { it.release.guid })
    }

    @Test
    fun `size sort is largest first`() {
        val rows = listOf(
            row("small", size = 1),
            row("large", size = 900),
            row("medium", size = 100),
        )

        val sorted = sortReleaseRows(rows, ReleaseSort.SIZE)

        assertEquals(listOf("large", "medium", "small"), sorted.map { it.release.guid })
    }

    @Test
    fun `releaseRow maps the wire fields and filters blank rejections`() {
        val resource = RadarrReleaseResource(
            guid = "g1",
            title = "A Movie 2020",
            indexer = "idx",
            size = 1_500_000_000,
            seeders = 4,
            leechers = 2,
            protocol = DownloadProtocol.TORRENT,
            age = 3,
            customFormatScore = 15,
            quality = QualityModel(
                quality = Quality(id = 1, name = "Bluray-2160p"),
                revision = Revision(version = 2),
            ),
            languages = listOf(Language(id = 1, name = "English")),
            rejections = listOf("Wrong quality", ""),
        )

        val ui = releaseRow(resource)

        assertEquals("BluRay 2160p", ui.qualityName)
        assertEquals(15, ui.qualityScore)
        assertEquals(3, ui.ageDays)
        assertEquals(listOf("Wrong quality"), ui.rejections)
        assertTrue(ui.hasRejections)
        assertEquals("English", ui.languageNames)
        assertEquals("1.4 GB", ui.sizeText)
    }

    @Test
    fun `applyGrabOverrides replaces only the chosen fields`() {
        val release = RadarrReleaseResource(
            guid = "g1",
            quality = QualityModel(
                quality = Quality(id = 1, name = "WEBDL-1080p"),
                revision = Revision(version = 3),
            ),
            languages = listOf(
                Language(id = 1, name = "English"),
                Language(id = 2, name = "French"),
            ),
        )
        val overrideQuality = Quality(id = 8, name = "Remux-1080p")
        val overrideLanguage = Language(id = 4, name = "German")

        val both = applyGrabOverrides(release, overrideQuality, overrideLanguage)
        val neither = applyGrabOverrides(release, null, null)

        assertEquals(8, both.quality.quality?.id)
        assertEquals(3, both.quality.revision?.version)
        assertEquals(listOf(4), both.languages?.map { it.id })
        assertEquals(listOf(1, 2), neither.languages?.map { it.id })
        assertEquals(1, neither.quality.quality?.id)
    }
}
