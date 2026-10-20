package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.NamingConfigResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrMediaManagementRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeLidarrSystem()
        fake.mediaManagementConfig = MediaManagementConfigResource(
            id = 1,
            copyUsingHardlinks = true,
            minimumFreeSpaceWhenImporting = 256,
            chmodFolder = "775",
        )
        fake.namingConfig = NamingConfigResource(
            id = 1,
            renameTracks = true,
            standardTrackFormat = "{Artist Name} ({Album ReleaseYear})",
        )
        store = FakeEntityStore()
    }

    private fun TestScope.repository() = LidarrMediaManagementRepository(
        serverId = serverId,
        store = store,
        mediaManagementApi = fake,
        namingApi = fake,
        actionQueue = ActionQueueImpl(store),
        now = { 1_000L },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun <T> ok(result: ArrResult<T>): T = (result as ArrResult.Ok).value

    @Test
    fun `refresh fills both config caches`() = runTest {
        val repository = repository()

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(fake.mediaManagementConfig, repository.cachedMediaManagement())
        assertEquals(fake.namingConfig, repository.cachedNaming())
        assertFalse(repository.isStale())
    }

    @Test
    fun `refreshIfStale skips the network while the TTL holds`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "getMediaManagementConfig") IOException("offline") else null }

        val result = repository.refreshIfStale()

        assertTrue(result is ArrResult.Ok)
        assertEquals(fake.mediaManagementConfig, repository.cachedMediaManagement())
    }

    @Test
    fun `refresh failure surfaces the error and keeps nothing cached`() = runTest {
        fake.error = { op -> if (op == "getMediaManagementConfig") IOException("offline") else null }
        val repository = repository()

        val result = repository.refresh()

        val error = (result as ArrResult.Err).error
        assertTrue(error is ArrError.Network)
        assertEquals(null, repository.cachedMediaManagement())
    }

    @Test
    fun `saving media management PUTs to the server and caches the response`() = runTest {
        val repository = repository()
        repository.refresh()
        val draft = fake.mediaManagementConfig.copy(importExtraFiles = true)

        val result = repository.saveMediaManagement(draft)

        assertTrue(ok(result) is WriteOutcome.Synced)
        assertEquals(draft, fake.mediaManagementConfig)
        assertEquals(draft, repository.cachedMediaManagement())
    }

    @Test
    fun `offline save applies optimistically and queues the PUT`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "updateMediaManagementConfig") IOException("offline") else null }
        val draft = fake.mediaManagementConfig.copy(setPermissionsLinux = true, chmodFolder = "770")

        val result = repository.saveMediaManagement(draft)

        val outcome = ok(result)
        assertTrue(outcome is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals(1, pending.size)
        assertEquals("PUT", pending.single().method)
        assertEquals("api/v1/config/mediamanagement/1", pending.single().path)
        assertNotNull(pending.single().bodyJson)
        // Optimistic: the cache already carries the intent.
        assertEquals(draft, repository.cachedMediaManagement())
        assertEquals(1, repository.queuedCount().first())
    }

    @Test
    fun `offline naming save queues its own PUT`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.error = { op -> if (op == "updateNamingConfig") IOException("offline") else null }
        val draft = fake.namingConfig.copy(
            colonReplacementFormat = 1,
            artistFolderFormat = "{Artist Name}",
        )

        val result = repository.saveNaming(draft)

        assertTrue(ok(result) is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals("api/v1/config/naming/1", pending.single().path)
        assertEquals(draft, repository.cachedNaming())
    }

    @Test
    fun `naming examples parse the server sample keys`() = runTest {
        fake.namingExamples = buildJsonObject {
            put("standardTrackFormat", "The Artist Name - The Album Title - 01 - Track Title (1)")
            put("multiDiscTrackFormat", "The Artist Name - The Album Title - 01 - 01 - Track Title (1)")
            put("artistFolderFormat", "The Artist Name")
        }
        val repository = repository()

        val result = repository.namingExamples(
            renameTracks = true,
            replaceIllegalCharacters = false,
            colonReplacementFormat = 0,
            standardTrackFormat = "{Artist Name}",
            multiDiscTrackFormat = "{Artist Name}",
            artistFolderFormat = "{Artist Name}",
        )

        val examples = ok(result)
        assertEquals("The Artist Name - The Album Title - 01 - Track Title (1)", examples.standardTrackExample)
        assertEquals("The Artist Name - The Album Title - 01 - 01 - Track Title (1)", examples.multiDiscTrackExample)
        assertEquals("The Artist Name", examples.artistFolderExample)
    }

    @Test
    fun `naming example failure surfaces an error without blocking`() = runTest {
        fake.error = { op -> if (op == "getNamingExamples") IOException("offline") else null }
        val repository = repository()

        val result = repository.namingExamples(
            renameTracks = true,
            replaceIllegalCharacters = false,
            colonReplacementFormat = 0,
            standardTrackFormat = "{Artist Name}",
            multiDiscTrackFormat = "{Artist Name}",
            artistFolderFormat = "{Artist Name}",
        )

        assertTrue(result is ArrResult.Err)
    }
}
