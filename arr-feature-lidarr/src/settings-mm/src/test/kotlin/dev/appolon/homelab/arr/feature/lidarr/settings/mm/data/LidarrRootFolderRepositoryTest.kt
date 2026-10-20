package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.FakeSettingsMmEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrRootFolderRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrSystem
    private lateinit var store: FakeSettingsMmEntityStore

    @Before
    fun setUp() {
        fake = FakeLidarrSystem()
        fake.rootFolders[1] = RootFolderResource(
            id = 1,
            path = "/music",
            accessible = true,
            freeSpace = 250L,
            totalSpace = 1_000L,
        )
        fake.rootFolders[2] = RootFolderResource(
            id = 2,
            path = "/data/flac",
            accessible = true,
            // Folder row without a total: the disk join supplies it.
            freeSpace = 500L,
            totalSpace = null,
        )
        fake.diskSpace += DiskSpaceResource(path = "/data", freeSpace = 500L, totalSpace = 2_000L)
        store = FakeSettingsMmEntityStore()
    }

    private fun TestScope.repository() = LidarrRootFolderRepository(
        serverId = serverId,
        store = store,
        rootFolderApi = fake,
        diskSpaceLoader = fake::getDiskSpace,
        actionQueue = ActionQueueImpl(store),
        now = { 1_000L },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun <T> ok(result: ArrResult<T>): T = (result as ArrResult.Ok).value

    @Test
    fun `refresh caches folders and disk space`() = runTest {
        val repository = repository()

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(2, repository.cachedFolders().size)
        assertFalse(repository.isStale())
    }

    @Test
    fun `free space fraction prefers the folder row and falls back to the joined disk`() = runTest {
        val repository = repository()
        repository.refresh()

        val joined = repository.observeWithDisk().first()

        val music = joined.first { it.folder.path == "/music" }
        assertEquals(0.75f, music.usedFraction!!, 0.0001f)
        assertNull(music.disk)
        val flac = joined.first { it.folder.path == "/data/flac" }
        // Folder knows free 500; disk supplies total 2000 -> 75% used.
        assertEquals(0.75f, flac.usedFraction!!, 0.0001f)
        assertNotNull(flac.disk)
    }

    @Test
    fun `folders with no total and no disk join render text only`() = runTest {
        fake.rootFolders[3] = RootFolderResource(id = 3, path = "/mnt/usb", freeSpace = 10L)
        val repository = repository()
        repository.refresh()

        val usb = repository.observeWithDisk().first().first { it.folder.path == "/mnt/usb" }

        assertNull(usb.usedFraction)
        assertEquals(10L, usb.freeBytes)
    }

    @Test
    fun `add PUTs through POST and caches the created folder`() = runTest {
        val repository = repository()
        repository.refresh()

        val result = repository.add("/new-music")

        assertTrue(ok(result) is WriteOutcome.Synced)
        assertEquals(3, fake.rootFolders.size)
        assertEquals("/new-music", repository.cachedFolders().first { it.id == 3 }.path)
    }

    @Test
    fun `offline add queues the POST and shows a placeholder row`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "addRootFolder") IOException("offline") else null }

        val result = repository.add("/offline-music")

        assertTrue(ok(result) is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals("POST", pending.single().method)
        assertEquals("api/v1/rootfolder", pending.single().path)
        assertNotNull(pending.single().bodyJson)
        val placeholder = repository.cachedFolders().first { it.path == "/offline-music" }
        assertTrue(placeholder.id < 0)
    }

    @Test
    fun `path edit updates through the real PUT endpoint`() = runTest {
        val repository = repository()
        repository.refresh()
        val old = repository.cachedFolders().first { it.id == 1 }

        val result = repository.move(old, "/music-new")

        assertTrue(ok(result) is WriteOutcome.Synced)
        assertEquals("/music-new", fake.rootFolders[1]?.path)
        assertEquals("/music-new", repository.cachedFolders().first { it.id == 1 }.path)
    }

    @Test
    fun `offline path edit queues one PUT and rewrites the cached path`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "updateRootFolder") IOException("offline") else null }
        val old = repository.cachedFolders().first { it.id == 1 }

        val result = repository.move(old, "/music-new")

        assertTrue(ok(result) is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals("PUT", pending.single().method)
        assertEquals("api/v1/rootfolder/1", pending.single().path)
        assertEquals("/music-new", repository.cachedFolders().first { it.id == 1 }.path)
    }

    @Test
    fun `remove deletes the cached row and queues offline`() = runTest {
        val repository = repository()
        repository.refresh()

        val synced = repository.remove(2)
        assertTrue(ok(synced) is WriteOutcome.Synced)
        assertEquals(1, repository.cachedFolders().size)

        fake.error = { op -> if (op == "deleteRootFolder") IOException("offline") else null }
        val queued = repository.remove(1)
        assertTrue(ok(queued) is WriteOutcome.Queued)
        assertTrue(repository.cachedFolders().isEmpty())
        val pending = ActionQueueImpl(store).pending()
        assertEquals("DELETE", pending.single().method)
        assertEquals("api/v1/rootfolder/1", pending.single().path)
        assertEquals(setOf(1), repository.queuedRemovedIds().first())
        assertEquals(1, repository.queuedCount().first())
    }
}
