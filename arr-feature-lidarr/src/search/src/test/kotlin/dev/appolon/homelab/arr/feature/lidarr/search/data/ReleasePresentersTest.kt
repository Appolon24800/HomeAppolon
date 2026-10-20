package dev.appolon.homelab.arr.feature.lidarr.search.data

import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePresentersTest {

    private fun release(
        guid: String,
        discography: Boolean = false,
        score: Int = 0,
        releaseWeight: Int = 0,
        seeders: Int? = null,
        ageHours: Double = 0.0,
        size: Long = 0L,
        approved: Boolean = false,
    ) = ReleaseResource(
        guid = guid,
        title = "Artist - Album 2019 FLAC $guid",
        discography = discography,
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
        artistName = "Artist",
        albumTitle = if (discography) null else "Album",
        quality = QualityModel(quality = Quality(id = 7, name = "FLAC")),
        customFormats = listOf(CustomFormatResource(id = 1, name = "Lossless")),
        rejections = listOf("Wrong quality"),
    )

    @Test
    fun `releaseRow maps every column the list shows`() {
        val row = releaseRow(release("g1", seeders = 12, score = 30, approved = true))

        assertEquals("Artist - Album 2019 FLAC g1", row.title)
        assertEquals("indexer-a", row.indexer)
        assertEquals("FLAC", row.qualityName)
        assertEquals(30, row.qualityScore)
        assertEquals("Lossless", row.customFormats)
        assertFalse(row.discography)
        assertEquals("Album", row.albumTitle)
        assertEquals("Artist", row.artistName)
        assertEquals(12, row.seeders)
        assertTrue(row.approved)
        assertTrue(row.hasRejections)
    }

    @Test
    fun `releaseRow tolerates a missing parsed quality`() {
        val row = releaseRow(release("g1").copy(quality = null))

        assertEquals("", row.qualityName)
        assertNull(row.release.quality)
    }

    @Test
    fun `album packs and discography releases split by the group filter`() {
        val rows = listOf(
            releaseRow(release("pack")),
            releaseRow(release("discog", discography = true)),
        )

        assertEquals(2, filterReleaseRows(rows, ReleaseGroupFilter.ALL).size)
        assertEquals(
            listOf("pack"),
            filterReleaseRows(rows, ReleaseGroupFilter.ALBUM_PACKS).map { it.release.guid },
        )
        assertEquals(
            listOf("discog"),
            filterReleaseRows(rows, ReleaseGroupFilter.DISCOGRAPHY).map { it.release.guid },
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
    fun `applyGrabOverrides replaces the quality and keeps the rest`() {
        val body = release("g1", approved = true).copy(
            quality = QualityModel(
                quality = Quality(id = 7, name = "FLAC"),
                revision = dev.appolon.homelab.arr.api.lidarr.model.Revision(version = 2),
            ),
        )

        val overridden = applyGrabOverrides(body, Quality(id = 9, name = "MP3-320"))

        assertEquals(9, overridden.quality?.quality?.id)
        assertEquals(2, overridden.quality?.revision?.version)
        assertEquals(body.guid, overridden.guid)
        assertEquals(body.approved, overridden.approved)
    }

    @Test
    fun `applyGrabOverrides builds a quality model when the parse had none`() {
        val body = release("g1").copy(quality = null)

        val overridden = applyGrabOverrides(body, Quality(id = 9, name = "MP3-320"))

        assertEquals(9, overridden.quality?.quality?.id)
    }

    @Test
    fun `applyGrabOverrides without a choice keeps the parsed value`() {
        val body = release("g1")

        val untouched = applyGrabOverrides(body, null)

        assertEquals(body.quality, untouched.quality)
    }
}
