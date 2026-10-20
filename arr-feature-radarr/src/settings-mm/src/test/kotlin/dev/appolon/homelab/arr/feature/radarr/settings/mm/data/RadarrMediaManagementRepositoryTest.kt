package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.api.radarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingExampleResource
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrMediaManagementRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrSystem
    private lateinit var store: FakeSettingsMmEntityStore

    @Before
    fun setUp() {
        fake = FakeRadarrSystem()
        fake.mediaManagementConfigState = MediaManagementConfigResource(
            id = 1,
            copyUsingHardlinks = true,
            minimumFreeSpaceWhenImporting = 256,
            chmodFolder = "775",
        )
        fake.namingConfigState = NamingConfigResource(
            id = 1,
            renameMovies = true,
            standardMovieFormat = "{Movie Title} ({Year})",
        )
        store = FakeSettingsMmEntityStore()
    }

    private fun TestScope.repository() = RadarrMediaManagementRepository(
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
        assertEquals(fake.mediaManagementConfigState, repository.cachedMediaManagement())
        assertEquals(fake.namingConfigState, repository.cachedNaming())
        assertFalse(repository.isStale())
    }

    @Test
    fun `refreshIfStale skips the network while the TTL holds`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.errorFor = { op -> if (op == "mediaManagementConfig") IOException("offline") else null }

        val result = repository.refreshIfStale()

        assertTrue(result is ArrResult.Ok)
        assertEquals(fake.mediaManagementConfigState, repository.cachedMediaManagement())
    }

    @Test
    fun `refresh failure surfaces the error and keeps nothing cached`() = runTest {
        fake.errorFor = { op -> if (op == "mediaManagementConfig") IOException("offline") else null }
        val repository = repository()

        val result = repository.refresh()

        val error = (result as ArrResult.Err).error
        assertTrue(error is dev.appolon.homelab.arr.core.ArrError.Network)
        assertEquals(null, repository.cachedMediaManagement())
    }

    @Test
    fun `saving media management PUTs to the server and caches the response`() = runTest {
        val repository = repository()
        repository.refresh()
        val draft = fake.mediaManagementConfigState.copy(importExtraFiles = true)

        val result = repository.saveMediaManagement(draft)

        assertTrue(ok(result) is WriteOutcome.Synced)
        assertEquals(draft, fake.mediaManagementConfigState)
        assertEquals(draft, repository.cachedMediaManagement())
    }

    @Test
    fun `offline save applies optimistically and queues the PUT`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.errorFor = { op -> if (op == "updateMediaManagementConfig") IOException("offline") else null }
        val draft = fake.mediaManagementConfigState.copy(setPermissionsLinux = true, chmodFolder = "770")

        val result = repository.saveMediaManagement(draft)

        val outcome = ok(result)
        assertTrue(outcome is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals(1, pending.size)
        assertEquals("PUT", pending.single().method)
        assertEquals("api/v3/config/mediamanagement/1", pending.single().path)
        assertNotNull(pending.single().bodyJson)
        // Optimistic: the cache already carries the intent.
        assertEquals(draft, repository.cachedMediaManagement())
        assertEquals(1, repository.queuedCount().first())
    }

    @Test
    fun `offline naming save queues its own PUT`() = runTest {
        val repository = repository()
        repository.refresh()
        fake.errorFor = { op -> if (op == "updateNamingConfig") IOException("offline") else null }
        val draft = fake.namingConfigState.copy(
            colonReplacementFormat = ColonReplacementFormat.DASH,
            movieFolderFormat = "{Movie Title}/{Collection}",
        )

        val result = repository.saveNaming(draft)

        assertTrue(ok(result) is WriteOutcome.Queued)
        val pending = ActionQueueImpl(store).pending()
        assertEquals("api/v3/config/naming/1", pending.single().path)
        assertEquals(draft, repository.cachedNaming())
    }

    @Test
    fun `naming examples return the server sample`() = runTest {
        fake.namingExamplesResult = NamingExampleResource(
            movieExample = "Server Movie (2010)",
            movieFolderExample = "ServerFolder",
        )
        val repository = repository()

        val result = repository.namingExamples(
            renameMovies = true,
            replaceIllegalCharacters = false,
            colonReplacementFormat = ColonReplacementFormat.SMART,
            standardMovieFormat = "{Movie Title}",
            movieFolderFormat = "{Movie Title}",
        )

        assertEquals(fake.namingExamplesResult, ok(result))
    }
}
