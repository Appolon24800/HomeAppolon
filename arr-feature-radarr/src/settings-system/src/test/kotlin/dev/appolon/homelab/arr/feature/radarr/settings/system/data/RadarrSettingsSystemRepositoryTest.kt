package dev.appolon.homelab.arr.feature.radarr.settings.system.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.BackupResource
import dev.appolon.homelab.arr.api.radarr.model.BackupType
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrSettingsSystemRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private lateinit var api: FakeRadarrSystem
    private lateinit var commands: FakeRadarrQueue
    private lateinit var repository: RadarrSettingsSystemRepository
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeEntityStore()
        queue = FakeSystemActionQueue()
        api = FakeRadarrSystem()
        commands = FakeRadarrQueue()
        store.clock = { now }
        repository = repository()
    }

    private fun repository() = RadarrSettingsSystemRepository(
        serverId = serverId,
        store = store,
        hostConfigApi = api,
        backupApi = api,
        updateApi = api,
        updateLogFileApi = api,
        systemApi = api,
        taskApi = api,
        diskSpaceApi = api,
        healthApi = api,
        tagApi = api,
        tagDetailsApi = api,
        autoTaggingApi = api,
        commandApi = commands,
        actionQueue = queue,
        now = { now },
    )

    private fun seed() {
        api.hostConfigState = HostConfigResource(
            id = 1,
            bindAddress = "*",
            port = 7878,
            urlBase = "/radarr",
            apiKey = "server-secret",
            password = "existing-password",
        )
        api.backups += BackupResource(id = 4, name = "radarr_backup.zip", type = BackupType.SCHEDULED, size = 2048)
        api.tagItems += TagResource(id = 1, label = "hd")
        api.tagUsage = mapOf(
            1 to TagDetailsResource(id = 1, label = "hd", movieIds = listOf(3, 7)),
        )
        api.autoTaggingItems += AutoTaggingResource(
            id = 2,
            name = "SSD movies",
            tags = setOf(1),
        )
    }

    // ------------------------------------------------------------ refresh

    @Test
    fun `refreshGeneral caches the host config without secrets`() = runTest {
        seed()
        val result = repository.refreshGeneral()

        assertTrue(result is ArrResult.Ok)
        val cached = repository.cachedHostConfig()!!
        assertEquals("/radarr", cached.urlBase)
        assertNull(cached.apiKey)
        assertNull(cached.password)
    }

    @Test
    fun `general TTL flips once the window passes`() = runTest {
        seed()
        assertTrue(repository.isGeneralStale())
        repository.refreshGeneral()
        assertFalse(repository.isGeneralStale())
        now += RadarrSettingsSystemRepository.SYSTEM_TTL_MILLIS + 1
        assertTrue(repository.isGeneralStale())
    }

    @Test
    fun `refreshSystem caches status tasks disk and health sorted`() = runTest {
        seed()
        api.taskItems += dev.appolon.homelab.arr.api.radarr.model.TaskResource(id = 1, name = "Backup", interval = 1440)
        api.diskSpaceItems += dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource(
            id = 1,
            path = "/media",
            freeSpace = 100,
            totalSpace = 400,
        )
        api.healthItems += dev.appolon.homelab.arr.api.radarr.model.HealthResource(id = 1)
        val result = repository.refreshSystem()

        assertTrue(result is ArrResult.Ok)
        assertEquals("Radarr", repository.cachedSystemStatus()!!.appName)
        assertEquals(1, repository.cachedTasks().size)
        assertEquals(1, repository.cachedDiskSpace().size)
        assertEquals(1, repository.cachedHealth().size)
    }

    @Test
    fun `refreshSystem surfaces the error when nothing is cached`() = runTest {
        api.errorFor = { IOException("offline") }
        val result = repository.refreshSystem()
        assertTrue((result as ArrResult.Err).error is ArrError.Network)
    }

    @Test
    fun `refreshUpdates caches updates status and update log files`() = runTest {
        seed()
        api.updateItems += dev.appolon.homelab.arr.api.radarr.model.UpdateResource(
            id = 1,
            version = "5.30.0.8710",
            installed = true,
            latest = true,
        )
        api.updateLogFileItems += dev.appolon.homelab.arr.api.radarr.model.LogFileResource(
            id = 1,
            filename = "update.1.txt",
        )
        val result = repository.refreshUpdates()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedUpdates().size)
        assertEquals("Radarr", repository.cachedSystemStatus()!!.appName)
        assertEquals(1, repository.cachedUpdateLogFiles().size)
    }

    @Test
    fun `refreshBackups lists backups newest first`() = runTest {
        seed()
        api.backups += BackupResource(
            id = 5,
            name = "radarr_newer.zip",
            type = BackupType.MANUAL,
            size = 4096,
            time = "2026-01-02T00:00:00Z",
        )
        repository.refreshBackups()

        val cached = repository.cachedBackups()
        assertEquals(listOf(5, 4), cached.map { it.id })
    }

    @Test
    fun `refreshTags caches tags and details`() = runTest {
        seed()
        val result = repository.refreshTags()

        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("hd"), repository.cachedTags().map { it.label })
        assertEquals(2, repository.cachedTagDetails().first().movieIds!!.size)
    }

    @Test
    fun `refreshAutoTaggingSchema caches and falls back offline`() = runTest {
        api.autoTaggingSchemaResult = listOf(
            dev.appolon.homelab.arr.api.radarr.model.AutoTaggingSpecificationSchema(
                implementation = "RootFolderSpecification",
                implementationName = "Root Folder",
            ),
        )
        val fresh = repository.refreshAutoTaggingSchema()
        assertTrue(fresh is ArrResult.Ok)
        assertEquals(1, (fresh as ArrResult.Ok).value.size)

        api.errorFor = { IOException("offline") }
        val cached = repository.refreshAutoTaggingSchema()
        assertTrue(cached is ArrResult.Ok)
        assertEquals(1, (cached as ArrResult.Ok).value.size)
    }

    // ------------------------------------------------------ host config write

    @Test
    fun `saveHostConfig PUTs online and caches the response stripped`() = runTest {
        seed()
        repository.refreshGeneral()
        val edited = repository.cachedHostConfig()!!.copy(port = 8989)

        val result = repository.saveHostConfig(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(8989, api.hostConfigState.port)
        assertNull(repository.cachedHostConfig()!!.apiKey)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `saveHostConfig offline queues without secrets and keeps the stamp`() = runTest {
        seed()
        repository.refreshGeneral()
        store.clock = { now }
        val stampBefore = store.lastFetchedAt(
            serverId,
            RadarrSettingsSystemRepository.HOST_CONFIG_TYPE,
        )
        api.errorFor = { IOException("offline") }
        val edited = repository.cachedHostConfig()!!.copy(
            port = 8321,
            password = "typed-secret",
        )

        val result = repository.saveHostConfig(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v3/config/host/1", action.path)
        assertFalse(action.bodyJson!!.contains("typed-secret"))
        assertFalse(action.bodyJson!!.contains("existing-password"))
        assertFalse(action.bodyJson!!.contains("server-secret"))
        assertEquals(8321, repository.cachedHostConfig()!!.port)
        assertEquals(stampBefore, store.lastFetchedAt(serverId, RadarrSettingsSystemRepository.HOST_CONFIG_TYPE))
    }

    @Test
    fun `auth failures never queue`() = runTest {
        seed()
        repository.refreshGeneral()
        api.errorFor = { retrofit2.HttpException(errorResponse(401)) }
        val result = repository.saveHostConfig(repository.cachedHostConfig()!!.copy(port = 1))

        assertTrue((result as ArrResult.Err).error is ArrError.Auth)
        assertTrue(queue.pending().isEmpty())
    }

    // ------------------------------------------------------------ backup actions

    @Test
    fun `createBackup dispatches the Backup command`() = runTest {
        val result = repository.createBackup()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Backup", commands.commandItems.single().name)
    }

    @Test
    fun `createBackup offline queues the command`() = runTest {
        commands.errorFor = { IOException("offline") }
        val result = repository.createBackup()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson!!.contains("\"Backup\""))
    }

    @Test
    fun `restoreBackup returns restartRequired and surfaces http errors`() = runTest {
        seed()
        api.restoreResult = dev.appolon.homelab.arr.api.radarr.model.BackupRestoreResource(restartRequired = true)
        val ok = repository.restoreBackup(4)
        assertTrue((ok as ArrResult.Ok).value.restartRequired)
        assertTrue(api.restoreRequested)

        val missing = repository.restoreBackup(99)
        assertTrue((missing as ArrResult.Err).error is ArrError.Http)
    }

    @Test
    fun `deleteBackup removes online and queues offline`() = runTest {
        seed()
        val synced = repository.deleteBackup(4)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.backups.isEmpty())

        api.backups += BackupResource(id = 6, name = "again.zip")
        repository.refreshBackups()
        api.errorFor = { IOException("offline") }
        val queued = repository.deleteBackup(6)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedBackups().isEmpty())
        assertEquals("api/v3/system/backup/6", queue.pending().single().path)
    }

    @Test
    fun `installUpdate dispatches the ApplicationUpdate command`() = runTest {
        val result = repository.installUpdate()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("ApplicationUpdate", commands.commandItems.single().name)
    }

    @Test
    fun `updateLogFileContents returns the plain text`() = runTest {
        api.updateLogFileContentsText = "2026-10-20 10:00:00.0|Info|Update installed"
        val result = repository.updateLogFileContents("update.1.txt")
        assertTrue((result as ArrResult.Ok).value.contains("Update installed"))
    }

    // ---------------------------------------------------------------- tag CRUD

    @Test
    fun `tag create and rename PUT through online`() = runTest {
        seed()
        val created = repository.saveTag(TagResource(label = "4k"))
        assertTrue((created as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(2, api.tagItems.size)
        assertEquals("4k", api.tagItems[1].label)

        val renamed = repository.saveTag(TagResource(id = 2, label = "uhd"))
        assertTrue((renamed as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("uhd", api.tagItems[1].label)
    }

    @Test
    fun `tag create offline uses a temp id and queues the POST`() = runTest {
        seed()
        api.errorFor = { IOException("offline") }
        val result = repository.saveTag(TagResource(label = "offline-tag"))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val cached = repository.cachedTags().first { it.label == "offline-tag" }
        assertTrue(cached.id < 0)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("api/v3/tag", queue.pending().single().path)
    }

    @Test
    fun `tag delete drops the row and details, online and offline`() = runTest {
        seed()
        repository.refreshTags()
        val synced = repository.deleteTag(1)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.tagItems.isEmpty())
        assertTrue(repository.cachedTags().isEmpty())
        assertTrue(repository.cachedTagDetails().isEmpty())

        api.tagItems += TagResource(id = 9, label = "temp")
        repository.refreshTags()
        api.errorFor = { IOException("offline") }
        val queued = repository.deleteTag(9)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedTags().isEmpty())
    }

    // --------------------------------------------------------- auto tagging CRUD

    @Test
    fun `autoTagging save updates online and queues offline`() = runTest {
        seed()
        val edited = api.autoTaggingItems.first().copy(name = "Renamed")
        val synced = repository.saveAutoTagging(edited)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Renamed", api.autoTaggingItems.first().name)

        api.errorFor = { IOException("offline") }
        val created = repository.saveAutoTagging(AutoTaggingResource(name = "New rule"))
        assertTrue((created as ArrResult.Ok).value is WriteOutcome.Queued)
        val cached = repository.cachedAutoTagging().first { it.name == "New rule" }
        assertTrue(cached.id < 0)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("api/v3/autotagging", queue.pending().single().path)
    }

    @Test
    fun `autoTagging delete syncs online and queues offline`() = runTest {
        seed()
        val synced = repository.deleteAutoTagging(2)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.autoTaggingItems.isEmpty())

        api.autoTaggingItems += AutoTaggingResource(id = 3, name = "Later")
        repository.refreshAutoTagging()
        api.errorFor = { IOException("offline") }
        val queued = repository.deleteAutoTagging(3)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/autotagging/3", queue.pending().single().path)
    }

    // ----------------------------------------------------------------- queue

    @Test
    fun `queuedCount counts this group's paths and command descriptions`() = runTest {
        seed()
        api.errorFor = { IOException("offline") }
        repository.saveHostConfig(repository.cachedHostConfig() ?: api.hostConfigState)
        repository.deleteTag(1)
        commands.errorFor = { IOException("offline") }
        repository.createBackup()
        queue.enqueue(queue.action("other", path = "api/v3/movie/editor"))
        queue.enqueue(
            queue.action("queue-owned", path = "api/v3/command").copy(description = "Movie search"),
        )

        assertEquals(3, repository.queuedCount().first())
    }

    @Test
    fun `queuedRemovedTagIds reads the queued delete paths`() = runTest {
        seed()
        api.errorFor = { IOException("offline") }
        repository.deleteTag(1)

        assertEquals(setOf(1), repository.queuedRemovedTagIds().first())
    }

    private fun errorResponse(code: Int): retrofit2.Response<Any> =
        retrofit2.Response.error(code, ByteArray(0).toResponseBody())
}
