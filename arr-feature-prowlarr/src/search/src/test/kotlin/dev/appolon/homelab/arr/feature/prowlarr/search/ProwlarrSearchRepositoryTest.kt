package dev.appolon.homelab.arr.feature.prowlarr.search

import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSearch
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSettings
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.appolon.homelab.arr.data.testing.FakeEntityStore

internal class SearchFixture {
    var now = 1_000_000L
    var io: CoroutineDispatcher = Dispatchers.Unconfined
    val store = FakeEntityStore().also { it.clock = { now } }
    val queue = ActionQueueImpl(store)
    val api = FakeProwlarrSearch()
    val indexers = FakeProwlarrIndexers()
    val settings = FakeProwlarrSettings()
    fun repository(server: String = "test") = ProwlarrSearchRepository(
        ServerId(server), store, queue, api, api, api, indexers, indexers, settings,
        now = { now }, io = io,
    )
}

class ProwlarrSearchRepositoryTest {
    @Test
    fun `search is cache first including empty results and expires at TTL`() = runTest {
        val f = SearchFixture()
        val request = SearchRequest("absent")
        assertEquals(ArrResult.ok(emptyList<ReleaseResource>()), f.repository().search(request))
        f.repository().search(request)
        assertEquals(1, f.api.calls.size)
        f.now += ProwlarrSearchRepository.TTL_MILLIS
        f.repository().search(request)
        assertEquals(2, f.api.calls.size)
    }

    @Test
    fun `search filters are canonical and forwarded without pagination truncation`() = runTest {
        val f = SearchFixture()
        val request = SearchRequest(" Movie ", linkedSetOf(4, 2), linkedSetOf(2000, 1000))
        f.repository().search(request)
        val args = f.api.calls.single().arguments
        assertEquals("Movie", args["query"])
        assertEquals(listOf(2, 4), args["indexerIds"])
        assertEquals(listOf(1000, 2000), args["categories"])
        assertNull(args["limit"])
        f.repository().search(SearchRequest("Movie", linkedSetOf(2, 4), linkedSetOf(1000, 2000)))
        assertEquals(1, f.api.calls.size)
    }

    @Test
    fun `forced refresh replaces cache and server caches remain isolated`() = runTest {
        val f = SearchFixture()
        f.api.releases += ReleaseResource(guid = "a")
        f.repository().search(SearchRequest())
        f.api.releases.clear()
        f.repository().search(SearchRequest(), true)
        assertEquals(emptyList<ReleaseResource>(), f.repository().cachedSearch(SearchRequest()))
        assertNull(f.repository("other").cachedSearch(SearchRequest()))
    }

    @Test
    fun `failed refresh preserves the last successful cached rows`() = runTest {
        val f = SearchFixture()
        val release = ReleaseResource(guid = "a")
        f.api.releases += release
        f.repository().search(SearchRequest())
        f.api.failure = { IOException("offline") }
        assertTrue(f.repository().search(SearchRequest(), true) is ArrResult.Err)
        assertEquals(listOf(release), f.repository().cachedSearch(SearchRequest()))
    }

    @Test
    fun `options and last request survive repository recreation offline`() = runTest {
        val f = SearchFixture()
        f.indexers.indexers[1] = IndexerResource(id = 1, name = "Indexer")
        f.indexers.defaultCategories += IndexerCategory(id = 2000, name = "Movies")
        f.settings.downloadClients[7] = DownloadClientResource(id = 7)
        f.repository().options()
        val request = SearchRequest("Movie", setOf(1), setOf(2000))
        f.repository().rememberRequest(request)
        f.indexers.failure = { IOException() }
        assertTrue(f.repository().options() is ArrResult.Ok)
        assertEquals(1, f.repository().cachedOptions()?.indexers?.size)
        assertEquals(request, f.repository().lastRequest())
    }

    @Test
    fun `query history filters and page form independent cache entries`() = runTest {
        val f = SearchFixture()
        f.api.history += HistoryResource(
            id = 1, indexerId = 2, eventType = HistoryEventType.INDEXER_QUERY,
        )
        f.repository().history(1, setOf(2))
        f.repository().history(2, setOf(2))
        val args = f.api.calls.first().arguments
        assertEquals(listOf(2), args["eventType"])
        assertEquals(listOf(2), args["indexerIds"])
        assertEquals(1, f.repository().cachedHistory(1, setOf(2))?.records?.size)
        assertTrue(f.repository().cachedHistory(2, setOf(2))?.records.isNullOrEmpty())
    }

    @Test
    fun `statistics forward selected window and cache each period separately`() = runTest {
        val f = SearchFixture()
        f.repository().stats(7)
        f.repository().stats(7)
        assertEquals(1, f.api.calls.size)
        val args = f.api.calls.single().arguments
        val start = java.time.Instant.parse(args["startDate"] as String)
        val end = java.time.Instant.parse(args["endDate"] as String)
        assertEquals(7L, java.time.Duration.between(start, end).toDays())
        f.repository().stats(30)
        assertNotNull(f.repository().cachedStats(30))
        assertEquals(2, f.api.calls.size)
    }

    @Test
    fun `online grab uses selected client without queueing`() = runTest {
        val f = SearchFixture()
        assertEquals(ArrResult.ok(false), f.repository().grab(ReleaseResource(guid = "a"), 9))
        assertEquals(9, f.api.grabs.single().downloadClientId)
        assertTrue(f.queue.pending().isEmpty())
    }

    @Test
    fun `offline grabs persist complete body deduplicate and expose queue keys`() = runTest {
        val f = SearchFixture()
        f.api.failure = { IOException() }
        val release = ReleaseResource(guid = "a", indexerId = 2, magnetUrl = "magnet:?xt=test")
        assertEquals(ArrResult.ok(true), f.repository().grab(release, 9))
        f.repository().grab(release, 9)
        val action = f.queue.pending().single()
        assertEquals("api/v1/search", action.path)
        assertEquals("POST", action.method)
        assertEquals(release.copy(downloadClientId = 9), ArrJson.decodeFromString(
            ReleaseResource.serializer(), action.bodyJson!!,
        ))
        assertEquals(setOf(release.rowKey()), f.repository().queuedReleaseKeys().first())
        assertTrue(f.repository("other").queuedReleaseKeys().first().isEmpty())
        f.queue.remove(action.actionId)
        assertTrue(f.repository().queuedReleaseKeys().first().isEmpty())
    }

    @Test
    fun `authentication failure does not queue a grab`() = runTest {
        val f = SearchFixture()
        f.api.errorStatus = { 401 }
        val result = f.repository().grab(ReleaseResource(), 9)
        assertTrue(result is ArrResult.Err && result.error is ArrError.Auth)
        assertTrue(f.queue.pending().isEmpty())
    }

    @Test
    fun `cancellation is never swallowed or queued`() = runTest {
        val f = SearchFixture()
        f.api.failure = { CancellationException("cancel") }
        var cancelled = false
        try {
            f.repository().grab(ReleaseResource(), 9)
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(f.queue.pending().isEmpty())
    }

    @Test
    fun `sort null seeders last and parse only explicit quality tokens`() {
        val old = ReleaseResource(guid = "old", age = 3, size = 100, seeders = null)
        val new = ReleaseResource(guid = "new", age = 1, size = 200, seeders = 4)
        assertEquals(new, sortedReleases(listOf(old, new), SearchSort.AGE).first())
        assertEquals(new, sortedReleases(listOf(old, new), SearchSort.SIZE).first())
        assertEquals(new, sortedReleases(listOf(old, new), SearchSort.SEEDERS).first())
        assertEquals("1080p", ReleaseResource(title = "Movie.WEB.1080p").qualityLabel())
        assertNull(ReleaseResource(title = "Movie").qualityLabel())
        assertFalse(old.copy(indexerId = 2).rowKey() == old.rowKey())
    }
}
