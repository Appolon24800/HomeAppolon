package dev.appolon.homelab.arr.feature.prowlarr.indexers.data

import dev.appolon.homelab.arr.api.prowlarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerPrivacy
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexersPresentationTest {

    private val now = 1_000_000L

    private fun indexer(
        id: Int,
        name: String,
        enable: Boolean = true,
        protocol: DownloadProtocol = DownloadProtocol.TORRENT,
        privacy: IndexerPrivacy = IndexerPrivacy.PUBLIC,
        priority: Int = 25,
        tags: List<Int> = emptyList(),
    ) = IndexerResource(
        id = id,
        name = name,
        sortName = name,
        enable = enable,
        protocol = protocol,
        privacy = privacy,
        priority = priority,
        tags = tags,
    )

    private val indexers = listOf(
        indexer(1, "Alpha", protocol = DownloadProtocol.USENET),
        indexer(2, "Beta", enable = false, privacy = IndexerPrivacy.PRIVATE),
        indexer(3, "Gamma", tags = listOf(7)),
    )

    @Test
    fun `search matches name case insensitively`() {
        val out = IndexersPresentation.present(
            indexers,
            emptyMap(),
            IndexerListQuery(search = "gam"),
            nowMillis = now,
        )
        assertEquals(listOf("Gamma"), out.map { it.name })
    }

    @Test
    fun `protocol privacy and state filters compose`() {
        val query = IndexerListQuery(
            protocol = ProtocolFilter.TORRENT,
            privacy = PrivacyFilter.PUBLIC,
            state = StateFilter.ENABLED,
        )
        val out = IndexersPresentation.present(indexers, emptyMap(), query, nowMillis = now)
        assertEquals(listOf("Gamma"), out.map { it.name })
    }

    @Test
    fun `tag filter matches indexer tags`() {
        val out = IndexersPresentation.present(
            indexers,
            emptyMap(),
            IndexerListQuery(tagId = 7),
            nowMillis = now,
        )
        assertEquals(listOf("Gamma"), out.map { it.name })
    }

    @Test
    fun `throttled indexers count as not enabled and expose a state filter`() {
        val statuses = mapOf(
            1 to IndexerStatusResource(indexerId = 1, disabledTill = "2030-01-01T00:00:00Z"),
        )
        val enabled = IndexersPresentation.present(
            indexers,
            statuses,
            IndexerListQuery(state = StateFilter.ENABLED),
            nowMillis = now,
        )
        assertEquals(listOf("Gamma"), enabled.map { it.name })

        val throttled = IndexersPresentation.present(
            indexers,
            statuses,
            IndexerListQuery(state = StateFilter.THROTTLED),
            nowMillis = now,
        )
        assertEquals(listOf("Alpha"), throttled.map { it.name })
    }

    @Test
    fun `sorting by priority descending orders the list`() {
        val mixed = listOf(
            indexer(1, "A", priority = 25),
            indexer(2, "B", priority = 50),
            indexer(3, "C", priority = 10),
        )
        val out = IndexersPresentation.present(
            mixed,
            emptyMap(),
            IndexerListQuery(sortId = IndexerSorts.PRIORITY, sortDescending = true),
            nowMillis = now,
        )
        assertEquals(listOf("B", "A", "C"), out.map { it.name })
    }
}
