package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.UnmappedFolder
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.mm.FakeSettingsMmEntityStore
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

class RadarrRootFolderRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrSystem
    private lateinit var store: FakeSettingsMmEntityStore

    @Before
    fun setUp() {
        fake = FakeRadarrSystem()
        fake.rootFolderItems += RootFolderResource(
            id = 1,
            path = "/mnt/movies",
            accessible = true,
            freeSpace = 25L,
            unmappedFolders = listOf(UnmappedFolder(name = "Unknown Movie")),
        )
        fake.diskSpaceItems += DiskSpaceResource(
            path = "/",
            freeSpace = 500L,
            totalSpace = 1000L,
        )
        fake.diskSpaceItems += DiskSpaceResource(
            path = "/mnt",
            freeSpace = 25L,
            totalSpace = 100L,
        )
        store = FakeSettingsMmEntityStore()
    }

    private fun TestScope.repository() = RadarrRootFolderRepository(
        serverId = serverId,
        store = store,
        rootFolderApi = fake,
        diskSpaceLoader = fake::diskSpace,
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
        assertEquals("/mnt/movies", entry.folder.path)
        assertNotNull(entry.disk)
        assertEquals("/mnt", entry.disk?.path)
        // (100 - 25) / 100 used.
        assertEquals(0.75f, entry.usedFraction!!, 0.0001f)
        assertEquals(1, entry.unmappedCount)
    }

    @Test
    fun `folders without a matching disk carry no fraction`() = runTest {
        fake.diskSpaceItems.clear()
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

        val result = repository.add("/media/movies")

        assertTrue(ok(result) is WriteOutcome.Synced)
        val cached = repository.cachedFolders()
        assertTrue(cached.any { it.path == "/media/movies" })
    }

    @Test
    fun `offline add queues the POST and stages a placeholder`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.errorFor = { op -> if (op == "createRootFolder") IOException("offline") else null }

        val result = repository.add("/media/movies")

        assertTrue(ok(result) is WriteOutcome.Queued)
        val staged = repository.cachedFolders().first { it.path == "/media/movies" }
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
        fake.errorFor = { op -> if (op == "removeRootFolder") IOException("offline") else null }

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
        fake.errorFor = { op ->
            if (op == "createRootFolder" || op == "removeRootFolder") IOException("offline") else null
        }

        val result = repository.move(fake.rootFolderItems.single(), "/mnt/films")

        assertTrue(ok(result) is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals(2, pending.size)
        assertEquals("POST", pending[0].method)
        assertEquals("api/v3/rootfolder", pending[0].path)
        assertEquals("DELETE", pending[1].method)
        assertEquals("api/v3/rootfolder/1", pending[1].path)
        // Optimistic cache: new path staged, old row cleared.
        assertTrue(repository.cachedFolders().any { it.path == "/mnt/films" })
        assertTrue(repository.cachedFolders().none { it.path == "/mnt/movies" })
    }

    @Test
    fun `remove syncs online and drops the cached row`() = runTest {
        val repository = repository()
        repository.refresh()

        val result = repository.remove(1)

        assertTrue(ok(result) is WriteOutcome.Synced)
        assertTrue(repository.cachedFolders().isEmpty())
        assertTrue(fake.rootFolderItems.isEmpty())
    }
}
