package dev.appolon.homelab.arr.feature.prowlarr.history.data

import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryPresentationTest {

    private val indexers = listOf(
        IndexerResource(id = 1, name = "Alpha"),
        IndexerResource(id = 2, name = "Beta"),
    )

    private fun event(
        id: Int,
        indexerId: Int = 1,
        date: String? = null,
        type: HistoryEventType = HistoryEventType.INDEXER_QUERY,
        data: Map<String, String?>? = null,
    ) = HistoryResource(
        id = id,
        indexerId = indexerId,
        date = date,
        eventType = type,
        data = data,
        successful = true,
    )

    @Test
    fun `rows decode the wire data payload`() {
        val rows = HistoryPresentation.present(
            listOf(
                event(
                    1,
                    data = mapOf(
                        "query" to "ubuntu",
                        "elapsedTime" to "123",
                        "cached" to "1",
                        "queryResults" to "42",
                        "queryType" to "search",
                        "source" to "Sonarr",
                        "host" to "localhost",
                        "url" to "https://example.test/tor?rss",
                    ),
                ),
            ),
            indexers,
        )
        val row = rows.single()
        assertEquals("Alpha", row.indexerName)
        assertEquals("ubuntu", row.title)
        assertEquals(123, row.elapsedMs)
        assertEquals(true, row.cached)
        assertEquals(42, row.resultCount)
        assertEquals("search", row.queryType)
        assertEquals("Sonarr", row.source)
        assertEquals("localhost", row.host)
    }

    @Test
    fun `grabs fall back to grabTitle and unknown indexers keep their id`() {
        val rows = HistoryPresentation.present(
            listOf(
                event(9, indexerId = 3, type = HistoryEventType.RELEASE_GRABBED)
                    .copy(data = mapOf("grabTitle" to "Some.Release.1080p")),
            ),
            indexers,
        )
        val row = rows.single()
        assertEquals("Some.Release.1080p", row.title)
        assertEquals("#3", row.indexerName)
        assertNull(row.cached)
    }

    @Test
    fun `rows sort newest first with id tiebreak`() {
        val rows = HistoryPresentation.present(
            listOf(
                event(1, date = "2026-10-19T10:00:00Z"),
                event(5, date = "2026-10-20T10:00:00Z"),
                event(2, date = "2026-10-20T10:00:00Z"),
            ),
            indexers,
        )
        assertEquals(listOf(5, 2, 1), rows.map { it.id })
    }

    @Test
    fun `query filter matches title indexer source host and type`() {
        val events = listOf(
            event(1, data = mapOf("query" to "ubuntu")),
            event(2, indexerId = 2, data = mapOf("query" to "debian")),
            event(3, data = mapOf("query" to "fedora", "source" to "Radarr")),
        )
        val presented = { text: String -> HistoryPresentation.present(events, indexers, text) }
        assertEquals(listOf(1), presented("ubuntu").map { it.id })
        assertEquals(listOf(2), presented("beta").map { it.id })
        assertEquals(listOf(3), presented("radarr").map { it.id })
        assertEquals(3, presented("  ").size)
    }
}
