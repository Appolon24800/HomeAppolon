package dev.appolon.homelab.arr.feature.sonarr.settings.mm.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.UnmappedFolder
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.FakeSettingsMmEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonarrRootFolderRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrSystem
    private lateinit var store: FakeSettingsMmEntityStore

    @Before
    fun setUp() {
        fake = FakeSonarrSystem()
        fake.rootFolderStore[1] = RootFolderResource(
            id = 1,
            path = "/tv/series",
            accessible = true,
            freeSpace = 25L,
            totalSpace = 100L,
            unmappedFolders = listOf(UnmappedFolder(name = "Unknown Series")),
        )
        fake.diskSpace += DiskSpaceResource(
            path = "/",
            freeSpace = 500L,
            totalSpace = 1000L,
        )
        fake.diskSpace += DiskSpaceResource(
            path = "/tv",
            freeSpace = 25L,
            totalSpace = 100L,
        )
        store = FakeSettingsMmEntityStore()
    }

    private fun TestScope.repository() = SonarrRootFolderRepository(
        serverId = serverId,
        store = store,
        rootFolderApi = fake.rootFolderApi,
        diskSpaceLoader = fake.diskSpaceApi::list,
        actionQueue = ActionQueueImpl(store),
        now = { 1_000L },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun <T> ok(result: ArrResult<T>): T = (result as ArrResult.Ok).value

    @Test
    fun `refresh caches folders and joins the deepest matching disk`() = runTest {
        val repository = repository()

        repository.refresh()

        val joined = repository.observeWithDisk().first()
        assertEquals(1, joined.size)
        val entry = joined.single()
        assertEquals("/tv/series", entry.folder.path)
        assertNotNull(entry.disk)
        assertEquals("/tv", entry.disk?.path)
        assertEquals(1, entry.unmappedCount)
    }

    @Test
    fun `the folder's own totals drive the fraction when present`() = runTest {
        val repository = repository()

        repository.refresh()

        val entry = repository.observeWithDisk().first().single()
        // Folder reports 25 free of 100 total.
        assertEquals(0.75f, entry.usedFraction!!, 0.0001f)
        assertEquals(25L, entry.freeSpace)
        assertEquals(100L, entry.totalSpace)
    }

    @Test
    fun `folders without their own totals fall back to the joined disk`() = runTest {
        fake.rootFolderStore[1] = fake.rootFolderStore.getValue(1).copy(totalSpace = 0L)
        val repository = repository()

        repository.refresh()

        val entry = repository.observeWithDisk().first().single()
        // No folder total, so the /tv mount provides the numbers.
        assertEquals(0.75f, entry.usedFraction!!, 0.0001f)
        assertEquals(100L, entry.totalSpace)
    }

    @Test
    fun `folders without a total anywhere carry no fraction`() = runTest {
        fake.rootFolderStore[1] = fake.rootFolderStore.getValue(1).copy(totalSpace = 0L)
        fake.diskSpace.clear()
        val repository = repository()

        repository.refresh()

        val entry = repository.observeWithDisk().first().single()
        assertNull(entry.disk)
        assertNull(entry.usedFraction)
    }

    @Test
    fun `add syncs online and caches the created folder`() = runTest {
        val repository = repository()
        repository.refresh()

        val result = repository.add("/media/tv")

        assertTrue(ok(result) is WriteOutcome.Synced)
        val cached = repository.cachedFolders()
        assertTrue(cached.any { it.path == "/media/tv" })
    }

    @Test
    fun `offline add queues the POST and stages a placeholder`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "RootFolder.create") IOException("offline") else null }

        val result = repository.add("/media/tv")

        assertTrue(ok(result) is WriteOutcome.Queued)
        val staged = repository.cachedFolders().first { it.path == "/media/tv" }
        assertTrue(staged.id < 0)
        val pending = ActionQueueImpl(store).pending()
        assertEquals(1, pending.size)
        assertEquals("POST", pending.single().method)
        assertEquals("api/v3/rootfolder", pending.single().path)
        assertNotNull(pending.single().bodyJson)
    }

    @Test
    fun `offline remove clears the row and flags it queued`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "RootFolder.delete") IOException("offline") else null }

        val result = repository.remove(1)

        assertTrue(ok(result) is WriteOutcome.Queued)
        assertTrue(repository.cachedFolders().none { it.id == 1 })
        assertEquals(setOf(1), repository.queuedRemovedIds().first())
        assertEquals(1, repository.queuedCount().first())
    }

    @Test
    fun `offline move enqueues create before delete`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op ->
            if (op == "RootFolder.create" || op == "RootFolder.delete") IOException("offline") else null
        }

        val result = repository.move(fake.rootFolderStore.getValue(1), "/tv/shows")

        assertTrue(ok(result) is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals(2, pending.size)
        assertEquals("POST", pending[0].method)
        assertEquals("api/v3/rootfolder", pending[0].path)
        assertEquals("DELETE", pending[1].method)
        assertEquals("api/v3/rootfolder/1", pending[1].path)
        // Optimistic cache: new path staged, old row cleared.
        assertTrue(repository.cachedFolders().any { it.path == "/tv/shows" })
        assertTrue(repository.cachedFolders().none { it.path == "/tv/series" })
    }

    @Test
    fun `remove syncs online and drops the cached row`() = runTest {
        val repository = repository()
        repository.refresh()

        val result = repository.remove(1)

        assertTrue(ok(result) is WriteOutcome.Synced)
        assertTrue(repository.cachedFolders().isEmpty())
        assertTrue(fake.rootFolderStore.isEmpty())
    }
}
