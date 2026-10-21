package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassSeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers the optimistic merges behind queued and bodyless editor writes. */
class SeriesEditorMergeTest {

    private fun series(
        id: Int,
        monitored: Boolean = true,
        tags: Set<Int> = emptySet(),
        type: SeriesTypes = SeriesTypes.STANDARD,
    ) = SeriesResource(
        id = id,
        title = "S$id",
        monitored = monitored,
        seriesType = type,
        tags = tags.ifEmpty { null },
        seasons = listOf(
            SeasonResource(seasonNumber = 1, monitored = true),
            SeasonResource(seasonNumber = 2, monitored = false),
        ),
    )

    @Test
    fun `null editor fields stay untouched`() {
        val editor = SeriesEditorResource(seriesIds = listOf(1))
        val out = applySeriesEditor(listOf(series(1)), editor).single()
        assertEquals(true, out.monitored)
        assertEquals(SeriesTypes.STANDARD, out.seriesType)
        assertNull(out.rootFolderPath)
    }

    @Test
    fun `editor applies monitored quality folder and season folder`() {
        val editor = SeriesEditorResource(
            seriesIds = listOf(1, 2),
            monitored = false,
            qualityProfileId = 7,
            seasonFolder = true,
            rootFolderPath = "/tv/new",
        )
        val out = applySeriesEditor(listOf(series(1), series(2), series(3)), editor)
        assertEquals(false, out[0].monitored)
        assertEquals(false, out[1].monitored)
        assertEquals(7, out[0].qualityProfileId)
        assertEquals(true, out[0].seasonFolder)
        assertEquals("/tv/new", out[0].rootFolderPath)
        // Untargeted series are returned unchanged.
        assertEquals(true, out[2].monitored)
    }

    @Test
    fun `tags add remove and replace follow applyTags`() {
        val base = listOf(series(1, tags = setOf(1, 2)))
        val add = applySeriesEditor(
            base,
            SeriesEditorResource(seriesIds = listOf(1), tags = listOf(3), applyTags = ApplyTags.ADD),
        ).single()
        assertEquals(setOf(1, 2, 3), add.tags)

        val remove = applySeriesEditor(
            base,
            SeriesEditorResource(
                seriesIds = listOf(1),
                tags = listOf(2),
                applyTags = ApplyTags.REMOVE,
            ),
        ).single()
        assertEquals(setOf(1), remove.tags)

        val replace = applySeriesEditor(
            base,
            SeriesEditorResource(
                seriesIds = listOf(1),
                tags = listOf(9),
                applyTags = ApplyTags.REPLACE,
            ),
        ).single()
        assertEquals(setOf(9), replace.tags)
    }

    @Test
    fun `season pass applies monitored and replaces season flags`() {
        val pass = SeasonPassResource(
            series = listOf(
                SeasonPassSeriesResource(
                    id = 1,
                    monitored = false,
                    seasons = listOf(
                        SeasonResource(seasonNumber = 1, monitored = false),
                        SeasonResource(seasonNumber = 2, monitored = false),
                    ),
                ),
            ),
        )
        val out = applySeasonPass(listOf(series(1), series(2)), pass)
        assertEquals(false, out[0].monitored)
        assertEquals(listOf(false, false), out[0].seasons!!.map { it.monitored })
        // Untouched series keeps its flags.
        assertEquals(listOf(true, false), out[1].seasons!!.map { it.monitored })
    }

    @Test
    fun `tag parsing accepts comma space and semicolon separated ids`() {
        assertEquals(listOf(1, 2, 3, 4), parseTagIds("1, 2 3;4"))
        assertEquals(emptyList<Int>(), parseTagIds(""))
        assertNull(parseTagIds("1,x"))
    }
}
