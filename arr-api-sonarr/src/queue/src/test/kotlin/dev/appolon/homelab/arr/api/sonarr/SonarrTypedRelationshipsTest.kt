package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.ParseResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonarrTypedRelationshipsTest {
    @Test
    fun allTenRelationshipsDecodeAndRoundTripAsSpecResources() {
        val json = checkNotNull(javaClass.classLoader!!.getResource(
            "fixtures/sonarr/typed/relationships.json"
        )).readText()
        val parse = ArrJson.decodeFromString<ParseResource>(json)
        val queue = ArrJson.decodeFromString<QueueResource>(json)
        val blocklist = ArrJson.decodeFromString<BlocklistResource>(json)
        val history = ArrJson.decodeFromString<HistoryResource>(json)
        val manual = ArrJson.decodeFromString<ManualImportResource>(json)
        val reprocess = ArrJson.decodeFromString<ManualImportReprocessResource>(json)
        listOf(parse.series, queue.series, blocklist.series, history.series, manual.series)
            .forEach(::assertSeries)
        listOf(parse.episodes!!.single(), queue.episode, history.episode,
            manual.episodes!!.single(), reprocess.episodes!!.single()).forEach(::assertEpisode)
        assertEquals(parse, ArrJson.decodeFromString<ParseResource>(ArrJson.encodeToString(parse)))
        assertEquals(queue, ArrJson.decodeFromString<QueueResource>(ArrJson.encodeToString(queue)))
        assertEquals(blocklist, ArrJson.decodeFromString<BlocklistResource>(ArrJson.encodeToString(blocklist)))
        assertEquals(history, ArrJson.decodeFromString<HistoryResource>(ArrJson.encodeToString(history)))
        assertEquals(manual, ArrJson.decodeFromString<ManualImportResource>(ArrJson.encodeToString(manual)))
        assertEquals(reprocess, ArrJson.decodeFromString<ManualImportReprocessResource>(ArrJson.encodeToString(reprocess)))
    }

    @Test
    fun omittedRelationshipsKeepDefaultsAndNullableListsAcceptNull() {
        assertEquals(SeriesResource(), ArrJson.decodeFromString<QueueResource>("{}").series)
        assertEquals(EpisodeResource(), ArrJson.decodeFromString<HistoryResource>("{}").episode)
        assertNull(ArrJson.decodeFromString<ParseResource>("{\"episodes\":null}").episodes)
        assertNull(ArrJson.decodeFromString<ManualImportResource>("{\"episodes\":null}").episodes)
        assertNull(ArrJson.decodeFromString<ManualImportReprocessResource>("{\"episodes\":null}").episodes)
    }

    private fun assertSeries(series: SeriesResource) {
        assertEquals(7, series.id)
        assertEquals(12345, series.tvdbId)
        assertEquals(2, series.seasons!!.single().seasonNumber)
    }

    private fun assertEpisode(episode: EpisodeResource) {
        assertEquals(12, episode.id)
        assertEquals(3, episode.episodeNumber)
        assertEquals("Example Series", episode.series.title)
        assertEquals(5000000000L, episode.episodeFile.size)
    }
}
