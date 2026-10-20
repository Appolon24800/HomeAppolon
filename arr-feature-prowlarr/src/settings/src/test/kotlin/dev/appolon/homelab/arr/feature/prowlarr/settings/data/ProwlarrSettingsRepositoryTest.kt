package dev.appolon.homelab.arr.feature.prowlarr.settings.data

import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSettings
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSystem
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientCategory
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.settings.FakeSettingsActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.settings.FakeSettingsEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.settings.settingsRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProwlarrSettingsRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrSettings
    private lateinit var indexers: FakeProwlarrIndexers
    private lateinit var system: FakeProwlarrSystem
    private lateinit var store: FakeSettingsEntityStore
    private lateinit var queue: FakeSettingsActionQueue
    private lateinit var repository: ProwlarrSettingsRepository

    @Before
    fun setUp() {
        fake = FakeProwlarrSettings()
        indexers = FakeProwlarrIndexers()
        system = FakeProwlarrSystem()
        store = FakeSettingsEntityStore()
        queue = FakeSettingsActionQueue()
        repository = settingsRepository(
            fake,
            indexers,
            system,
            store,
            queue,
            now = { 1_000L },
            io = dispatcher,
        )
    }

    @Test
    fun `refresh caches both provider tables and reconciles removals`() = runTest(dispatcher) {
        fake.downloadClients[1] = DownloadClientResource(id = 1, name = "qBittorrent", priority = 2)
        fake.downloadClients[2] = DownloadClientResource(id = 2, name = "SABnzbd", priority = 1)
        fake.notifications[5] = NotificationResource(id = 5, name = "Discord")

        assertTrue(repository.refresh() is ArrResult.Ok)
        assertEquals(listOf("qBittorrent", "SABnzbd"), repository.cachedDownloadClients().map { it.name })
        assertEquals(listOf("Discord"), repository.cachedNotifications().map { it.name })

        fake.downloadClients.remove(1)
        assertTrue(repository.refresh() is ArrResult.Ok)
        assertEquals(listOf("SABnzbd"), repository.cachedDownloadClients().map { it.name })
    }

    @Test
    fun `refreshIfStale skips the network inside the ttl`() = runTest(dispatcher) {
        fake.downloadClients[1] = DownloadClientResource(id = 1, name = "qBittorrent")
        fake.notifications[5] = NotificationResource(id = 5, name = "Discord")
        assertTrue(repository.refresh() is ArrResult.Ok)
        assertEquals(1, fake.calls.count { it.operation == "getDownloadClients" })

        assertTrue(repository.refreshIfStale() is ArrResult.Ok)
        assertEquals(1, fake.calls.count { it.operation == "getDownloadClients" })
    }

    @Test
    fun `saveDownloadClient updates the cache when online`() = runTest(dispatcher) {
        fake.downloadClients[1] = DownloadClientResource(id = 1, name = "Old")
        val saved = repository.saveDownloadClient(
            DownloadClientResource(id = 1, name = "New", enable = true),
        )
        assertTrue(saved is ArrResult.Ok && saved.value is WriteOutcome.Synced)
        assertEquals("New", fake.downloadClients[1]?.name)
        assertEquals("New", repository.cachedDownloadClients().single().name)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `offline create queues optimistically with a temporary id`() = runTest(dispatcher) {
        fake.failure = { throw IOException("offline") }
        val saved = repository.saveDownloadClient(
            DownloadClientResource(id = 0, name = "Draft"),
        )
        val outcome = (saved as ArrResult.Ok).value
        assertTrue(outcome is WriteOutcome.Queued)
        val cached = repository.cachedDownloadClients().single()
        assertTrue(cached.id < 0)
        assertEquals("Draft", cached.name)
        assertEquals("api/v1/downloadclient", queue.pending().single().path)
    }

    @Test
    fun `offline delete queues and drops the row optimistically`() = runTest(dispatcher) {
        fake.downloadClients[1] = DownloadClientResource(id = 1, name = "Old")
        assertTrue(repository.refresh() is ArrResult.Ok)
        fake.failure = { throw IOException("offline") }

        val removed = repository.removeDownloadClient(1)
        assertTrue((removed as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedDownloadClients().isEmpty())
        assertEquals("api/v1/downloadclient/1", queue.pending().single().path)
    }

    @Test
    fun `http failures never queue`() = runTest(dispatcher) {
        fake.errorStatus = { 400 }
        val saved = repository.saveDownloadClient(DownloadClientResource(id = 1, name = "Bad"))
        assertTrue(saved is ArrResult.Err && saved.error is ArrError.Http)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `tests are transient probes`() = runTest(dispatcher) {
        assertTrue(
            repository.testDownloadClient(DownloadClientResource(id = 1)) is ArrResult.Ok,
        )
        assertTrue(repository.testAllNotifications() is ArrResult.Ok)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `saveHostConfig strips secrets before caching or queueing`() = runTest(dispatcher) {
        fake.failure = { throw IOException("offline") }
        val config = HostConfigResource(
            id = 1,
            bindAddress = "0.0.0.0",
            port = 9696,
            password = "hunter2",
            apiKey = "abc",
            proxyPassword = "proxypass",
        )
        val saved = repository.saveHostConfig(config)
        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        val cached = repository.cachedHostConfig()
        assertNull(cached?.password)
        assertNull(cached?.apiKey)
        assertNull(cached?.proxyPassword)
        assertEquals("0.0.0.0", cached?.bindAddress)
        val queued = queue.pending().single()
        assertTrue("apiKey" !in (queued.bodyJson.orEmpty()))
        assertTrue("hunter2" !in (queued.bodyJson.orEmpty()))
    }

    @Test
    fun `refreshSystem caches status tasks and health`() = runTest(dispatcher) {
        system.status = SystemResource(appName = "Prowlarr", version = "1.0")
        system.tasks[3] = TaskResource(id = 3, name = "Housekeeping")
        system.health += HealthResource(id = 1, message = "Update available")

        assertTrue(repository.refreshSystem() is ArrResult.Ok)
        assertEquals("Prowlarr", repository.cachedSystemStatus()?.appName)
        assertEquals(listOf("Housekeeping"), repository.cachedTasks().map { it.name })
        assertEquals(listOf("Update available"), repository.cachedHealth().map { it.message })
    }

    @Test
    fun `notification events survive a save round trip`() = runTest(dispatcher) {
        val outcome = repository.saveNotification(
            NotificationResource(id = 0, name = "Mail", onGrab = true, supportsOnGrab = true),
        )
        assertTrue(outcome is ArrResult.Ok && outcome.value is WriteOutcome.Synced)
        val stored = fake.notifications.values.single()
        assertTrue(stored.onGrab)
    }

    @Test
    fun `queuedRemovedIds reports only this provider's pending deletes`() = runTest(dispatcher) {
        queue.enqueue(
            queue.action("a1", path = "api/v1/downloadclient/7").copy(method = "DELETE"),
        )
        queue.enqueue(
            queue.action("a2", path = "api/v1/notification/9").copy(method = "DELETE"),
        )
        val ids = repository
            .queuedRemovedIds(ProwlarrSettingsRepository.DOWNLOAD_CLIENT_PATH)
            .first()
        assertEquals(setOf(7), ids)
    }

    @Test
    fun `grab targets round trip through the wire model`() {
        val resource = DownloadClientResource(
            id = 1,
            supportsCategories = true,
            categories = listOf(
                DownloadClientCategory(clientCategory = "tv", categories = listOf(5000, 5030)),
                DownloadClientCategory(clientCategory = "movies", categories = listOf(2000)),
            ),
        )
        val targets = GrabTargets.of(resource)
        assertEquals(listOf("tv", "movies"), targets.map { it.clientCategory })

        val applied = GrabTargets.applyOnto(
            resource,
            targets +
                GrabTarget(clientCategory = "  ", categories = listOf(5000)) +
                GrabTarget(clientCategory = "audio", categories = listOf(3000)),
        )
        // Duplicate 5000 stays with its first mapping; the blank row drops.
        assertEquals(
            listOf("tv", "movies", "audio"),
            applied.categories.orEmpty().mapNotNull { it.clientCategory },
        )
        assertEquals(
            listOf(5000, 5030),
            applied.categories.orEmpty().first().categories,
        )
    }

    @Test
    fun `ensureTagIds creates only missing tags`() = runTest(dispatcher) {
        indexers.tags[1] = TagResource(id = 1, label = "existing")
        val ids = repository.ensureTagIds(listOf("existing", "fresh"))
        assertEquals(listOf(1, 101), (ids as ArrResult.Ok).value)
        assertNotNull(indexers.tags[101])
        assertEquals("fresh", indexers.tags[101]?.label)
    }
}
