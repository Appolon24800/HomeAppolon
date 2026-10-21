package dev.appolon.homelab.arr.feature.lidarr.settings.system.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.lidarr.model.BackupResource
import dev.appolon.homelab.arr.api.lidarr.model.BackupType
import dev.appolon.homelab.arr.api.lidarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class LidarrSettingsSystemRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private lateinit var api: FakeLidarrSystem
    private lateinit var commands: FakeLidarrQueue
    private lateinit var repository: LidarrSettingsSystemRepository
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeEntityStore()
        queue = FakeSystemActionQueue()
        api = FakeLidarrSystem()
        commands = FakeLidarrQueue()
        store.clock = { now }
        repository = repository()
    }

    private fun repository() = LidarrSettingsSystemRepository(
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
        ioContext = Dispatchers.Unconfined,
    )

    private fun seed() {
        api.hostConfig = HostConfigResource(
            id = 1,
            bindAddress = "*",
            port = 8686,
            urlBase = "/lidarr",
            apiKey = "server-secret",
            password = "existing-password",
        )
        api.backups[4] = BackupResource(
            id = 4,
            name = "lidarr_backup.zip",
            type = BackupType.SCHEDULED,
            size = 2048,
        )
        api.tags[1] = TagResource(id = 1, label = "vinyl")
        api.tagDetails[1] = TagDetailsResource(
            id = 1,
            label = "vinyl",
            artistIds = listOf(3, 7),
            autoTagIds = listOf(2),
        )
        api.autoTaggings[2] = AutoTaggingResource(
            id = 2,
            name = "Vinyl folder",
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
        assertEquals("/lidarr", cached.urlBase)
        assertNull(cached.apiKey)
        assertNull(cached.password)
    }

    @Test
    fun `general TTL flips once the window passes`() = runTest {
        seed()
        assertTrue(repository.isGeneralStale())
        repository.refreshGeneral()
        assertFalse(repository.isGeneralStale())
        now += LidarrSettingsSystemRepository.SYSTEM_TTL_MILLIS + 1
        assertTrue(repository.isGeneralStale())
    }

    @Test
    fun `refreshSystem caches status tasks disk and health`() = runTest {
        seed()
        api.systemStatus = dev.appolon.homelab.arr.api.lidarr.model.SystemResource(
            appName = "Lidarr",
            version = "2.9.6.4557",
        )
        api.tasks[1] = dev.appolon.homelab.arr.api.lidarr.model.TaskResource(
            id = 1,
            name = "Backup",
            interval = 1440,
        )
        api.diskSpace += dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource(
            id = 1,
            path = "/music",
            freeSpace = 100,
            totalSpace = 400,
        )
        api.health += dev.appolon.homelab.arr.api.lidarr.model.HealthResource(id = 1)
        val result = repository.refreshSystem()

        assertTrue(result is ArrResult.Ok)
        assertEquals("Lidarr", repository.cachedSystemStatus()!!.appName)
        assertEquals(1, repository.cachedTasks().size)
        assertEquals(1, repository.cachedDiskSpace().size)
        assertEquals(1, repository.cachedHealth().size)
    }

    @Test
    fun `refreshSystem surfaces the error when nothing is cached`() = runTest {
        api.error = { IOException("offline") }
        val result = repository.refreshSystem()
        assertTrue((result as ArrResult.Err).error is ArrError.Network)
    }

    @Test
    fun `refreshUpdates caches updates status and update log files`() = runTest {
        seed()
        api.systemStatus = dev.appolon.homelab.arr.api.lidarr.model.SystemResource(
            appName = "Lidarr",
            version = "2.9.6",
        )
        api.updates += dev.appolon.homelab.arr.api.lidarr.model.UpdateResource(
            id = 1,
            version = "2.9.6.4557",
            installed = true,
            latest = true,
        )
        api.updateLogFiles += dev.appolon.homelab.arr.api.lidarr.model.LogFileResource(
            id = 1,
            filename = "lidarr.update.txt",
        )
        val result = repository.refreshUpdates()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedUpdates().size)
        assertEquals("Lidarr", repository.cachedSystemStatus()!!.appName)
        assertEquals(1, repository.cachedUpdateLogFiles().size)
    }

    @Test
    fun `refreshBackups lists backups newest first`() = runTest {
        seed()
        api.backups[5] = BackupResource(
            id = 5,
            name = "lidarr_newer.zip",
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
        assertEquals(listOf("vinyl"), repository.cachedTags().map { it.label })
        assertEquals(2, repository.cachedTagDetails().first().artistIds!!.size)
    }

    // ------------------------------------------------------ host config write

    @Test
    fun `saveHostConfig PUTs online and caches the response stripped`() = runTest {
        seed()
        repository.refreshGeneral()
        val edited = repository.cachedHostConfig()!!.copy(port = 8687)

        val result = repository.saveHostConfig(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(8687, api.hostConfig.port)
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
            LidarrSettingsSystemRepository.HOST_CONFIG_TYPE,
        )
        api.error = { IOException("offline") }
        val edited = repository.cachedHostConfig()!!.copy(
            port = 8321,
            password = "typed-secret",
        )

        val result = repository.saveHostConfig(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v1/config/host/1", action.path)
        assertFalse(action.bodyJson!!.contains("typed-secret"))
        assertFalse(action.bodyJson!!.contains("existing-password"))
        assertFalse(action.bodyJson!!.contains("server-secret"))
        assertEquals(8321, repository.cachedHostConfig()!!.port)
        assertEquals(
            stampBefore,
            store.lastFetchedAt(serverId, LidarrSettingsSystemRepository.HOST_CONFIG_TYPE),
        )
    }

    @Test
    fun `auth failures never queue`() = runTest {
        seed()
        repository.refreshGeneral()
        api.error = { HttpException(errorResponse(401)) }
        val result = repository.saveHostConfig(repository.cachedHostConfig()!!.copy(port = 1))

        assertTrue((result as ArrResult.Err).error is ArrError.Auth)
        assertTrue(queue.pending().isEmpty())
    }

    // ------------------------------------------------------------ backup actions

    @Test
    fun `createBackup dispatches the Backup command`() = runTest {
        val result = repository.createBackup()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Backup", commands.commands.values.single().name)
    }

    @Test
    fun `createBackup offline queues the command`() = runTest {
        commands.error = { IOException("offline") }
        val result = repository.createBackup()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v1/command", action.path)
        assertTrue(action.bodyJson!!.contains("\"Backup\""))
    }

    @Test
    fun `restoreBackup succeeds and surfaces http errors`() = runTest {
        seed()
        val ok = repository.restoreBackup(4)
        assertTrue(ok is ArrResult.Ok)
        assertEquals(listOf(4), api.restoredBackupIds)

        api.error = { op -> if (op == "restoreBackup") HttpException(errorResponse(400)) else null }
        val missing = repository.restoreBackup(99)
        assertTrue((missing as ArrResult.Err).error is ArrError.Http)
    }

    @Test
    fun `deleteBackup removes online and queues offline`() = runTest {
        seed()
        val synced = repository.deleteBackup(4)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.backups.isEmpty())

        api.backups[6] = BackupResource(id = 6, name = "again.zip")
        repository.refreshBackups()
        api.error = { IOException("offline") }
        val queued = repository.deleteBackup(6)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedBackups().isEmpty())
        assertEquals("api/v1/system/backup/6", queue.pending().single().path)
    }

    @Test
    fun `installUpdate dispatches the ApplicationUpdate command`() = runTest {
        val result = repository.installUpdate()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("ApplicationUpdate", commands.commands.values.single().name)
    }

    @Test
    fun `updateLogFileContents renders the text body`() = runTest {
        api.logFileContents["lidarr.update.txt"] = "2026-10-20 10:00:00|Info|Update installed"
        val result = repository.updateLogFileContents("lidarr.update.txt")
        assertTrue((result as ArrResult.Ok).value.contains("Update installed"))
    }

    @Test
    fun `autoTaggingConditions parses the live schema`() = runTest {
        api.autoTaggingSchema = kotlinx.serialization.json.buildJsonObject {
            put("implementationName", "Root Folder")
            put("implementation", "RootFolderSpecification")
        }
        val result = repository.autoTaggingConditions()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, (result as ArrResult.Ok).value.size)
        assertEquals("RootFolderSpecification", result.value.single().implementation)
    }

    // ---------------------------------------------------------------- tag CRUD

    @Test
    fun `tag create and rename go through online`() = runTest {
        seed()
        val created = repository.saveTag(TagResource(label = "flac"))
        assertTrue((created as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(2, api.tags.size)
        assertEquals("flac", api.tags[2]!!.label)

        val renamed = repository.saveTag(TagResource(id = 2, label = "lossless"))
        assertTrue((renamed as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("lossless", api.tags[2]!!.label)
    }

    @Test
    fun `tag create offline uses a temp id and queues the POST`() = runTest {
        seed()
        api.error = { IOException("offline") }
        val result = repository.saveTag(TagResource(label = "offline-tag"))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val cached = repository.cachedTags().first { it.label == "offline-tag" }
        assertTrue(cached.id < 0)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("api/v1/tag", queue.pending().single().path)
    }

    @Test
    fun `tag delete drops the row and details, online and offline`() = runTest {
        seed()
        repository.refreshTags()
        val synced = repository.deleteTag(1)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.tags.isEmpty())
        assertTrue(repository.cachedTags().isEmpty())
        assertTrue(repository.cachedTagDetails().isEmpty())

        api.tags[9] = TagResource(id = 9, label = "temp")
        repository.refreshTags()
        api.error = { IOException("offline") }
        val queued = repository.deleteTag(9)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedTags().isEmpty())
    }

    // --------------------------------------------------------- auto tagging CRUD

    @Test
    fun `autoTagging save updates online and queues offline`() = runTest {
        seed()
        val edited = api.autoTaggings.values.first().copy(name = "Renamed")
        val synced = repository.saveAutoTagging(edited)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Renamed", api.autoTaggings.values.first().name)

        api.error = { IOException("offline") }
        val created = repository.saveAutoTagging(AutoTaggingResource(name = "New rule"))
        assertTrue((created as ArrResult.Ok).value is WriteOutcome.Queued)
        val cached = repository.cachedAutoTagging().first { it.name == "New rule" }
        assertTrue(cached.id < 0)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("api/v1/autotagging", queue.pending().single().path)
    }

    @Test
    fun `autoTagging delete syncs online and queues offline`() = runTest {
        seed()
        val synced = repository.deleteAutoTagging(2)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.autoTaggings.isEmpty())

        api.autoTaggings[3] = AutoTaggingResource(id = 3, name = "Later")
        repository.refreshAutoTagging()
        api.error = { IOException("offline") }
        val queued = repository.deleteAutoTagging(3)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/autotagging/3", queue.pending().single().path)
    }

    // ----------------------------------------------------------------- queue

    @Test
    fun `queuedCount counts this group's paths and command descriptions`() = runTest {
        seed()
        api.error = { IOException("offline") }
        repository.saveHostConfig(repository.cachedHostConfig() ?: api.hostConfig)
        repository.deleteTag(1)
        commands.error = { IOException("offline") }
        repository.createBackup()
        queue.enqueue(queue.action("other", path = "api/v1/artist/editor"))
        queue.enqueue(
            queue.action("queue-owned", path = "api/v1/command").copy(description = "Artist search"),
        )

        assertEquals(3, repository.queuedCount().first())
    }

    @Test
    fun `queuedRemovedTagIds reads the queued delete paths`() = runTest {
        seed()
        api.error = { IOException("offline") }
        repository.deleteTag(1)

        assertEquals(setOf(1), repository.queuedRemovedTagIds().first())
    }

    private fun errorResponse(code: Int): retrofit2.Response<Any> =
        retrofit2.Response.error(code, ByteArray(0).toResponseBody())
}
