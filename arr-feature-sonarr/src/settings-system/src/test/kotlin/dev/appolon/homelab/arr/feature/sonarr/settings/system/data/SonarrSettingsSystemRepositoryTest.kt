package dev.appolon.homelab.arr.feature.sonarr.settings.system.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupType
import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.feature.sonarr.settings.system.FakeSystemEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class SonarrSettingsSystemRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var store: FakeSystemEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private lateinit var api: FakeSonarrSystem
    private lateinit var commands: FakeSonarrQueue
    private lateinit var repository: SonarrSettingsSystemRepository
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeSystemEntityStore()
        queue = FakeSystemActionQueue()
        api = FakeSonarrSystem()
        commands = FakeSonarrQueue()
        store.clock = { now }
        repository = repository()
    }

    private fun repository() = SonarrSettingsSystemRepository(
        serverId = serverId,
        store = store,
        hostConfigApi = api.hostConfigApi,
        backupApi = api.backupApi,
        updateApi = api.updateApi,
        updateLogFileApi = api.updateLogFileApi,
        systemApi = api.systemApi,
        taskApi = api.taskApi,
        diskSpaceApi = api.diskSpaceApi,
        healthApi = api.healthApi,
        tagApi = api.tagApi,
        tagDetailsApi = api.tagDetailsApi,
        autoTaggingApi = api.autoTaggingApi,
        commandApi = commands.command,
        actionQueue = queue,
        now = { now },
    )

    private fun seed() {
        api.hostConfig = HostConfigResource(
            id = 1,
            bindAddress = "*",
            port = 8989,
            urlBase = "/sonarr",
            apiKey = "server-secret",
            password = "existing-password",
        )
        api.backups += BackupResource(id = 4, name = "sonarr_backup.zip", type = BackupType.SCHEDULED, size = 2048)
        api.tagStore[1] = TagResource(id = 1, label = "anime")
        api.tagDetailsStore[1] = TagDetailsResource(
            id = 1,
            label = "anime",
            seriesIds = listOf(3, 7),
            autoTagIds = listOf(2),
        )
        api.autoTaggingStore[2] = AutoTaggingResource(
            id = 2,
            name = "Anime folder",
            tags = listOf(1),
        )
    }

    // ------------------------------------------------------------ refresh

    @Test
    fun `refreshGeneral caches the host config without secrets`() = runTest {
        seed()
        val result = repository.refreshGeneral()

        assertTrue(result is ArrResult.Ok)
        val cached = repository.cachedHostConfig()!!
        assertEquals("/sonarr", cached.urlBase)
        assertNull(cached.apiKey)
        assertNull(cached.password)
    }

    @Test
    fun `general TTL flips once the window passes`() = runTest {
        seed()
        assertTrue(repository.isGeneralStale())
        repository.refreshGeneral()
        assertFalse(repository.isGeneralStale())
        now += SonarrSettingsSystemRepository.SYSTEM_TTL_MILLIS + 1
        assertTrue(repository.isGeneralStale())
    }

    @Test
    fun `refreshSystem caches status tasks disk and health`() = runTest {
        seed()
        api.systemStatus = dev.appolon.homelab.arr.api.sonarr.model.SystemResource(
            appName = "Sonarr",
            version = "4.0.12.2278",
        )
        api.taskStore[1] = dev.appolon.homelab.arr.api.sonarr.model.TaskResource(
            id = 1,
            name = "Backup",
            interval = 1440,
        )
        api.diskSpace += dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource(
            id = 1,
            path = "/tv",
            freeSpace = 100,
            totalSpace = 400,
        )
        api.health += dev.appolon.homelab.arr.api.sonarr.model.HealthResource(id = 1)
        val result = repository.refreshSystem()

        assertTrue(result is ArrResult.Ok)
        assertEquals("Sonarr", repository.cachedSystemStatus()!!.appName)
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
        api.systemStatus = dev.appolon.homelab.arr.api.sonarr.model.SystemResource(
            appName = "Sonarr",
            version = "4.0.12",
        )
        api.updates += dev.appolon.homelab.arr.api.sonarr.model.UpdateResource(
            id = 1,
            version = "4.0.12.2278",
            installed = true,
            latest = true,
        )
        api.updateLogFiles += dev.appolon.homelab.arr.api.sonarr.model.LogFileResource(
            id = 1,
            filename = "sonarr.update.txt",
        )
        val result = repository.refreshUpdates()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedUpdates().size)
        assertEquals("Sonarr", repository.cachedSystemStatus()!!.appName)
        assertEquals(1, repository.cachedUpdateLogFiles().size)
    }

    @Test
    fun `refreshBackups lists backups newest first`() = runTest {
        seed()
        api.backups += BackupResource(
            id = 5,
            name = "sonarr_newer.zip",
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
        assertEquals(listOf("anime"), repository.cachedTags().map { it.label })
        assertEquals(2, repository.cachedTagDetails().first().seriesIds!!.size)
    }

    // ------------------------------------------------------ host config write

    @Test
    fun `saveHostConfig PUTs online and caches the response stripped`() = runTest {
        seed()
        repository.refreshGeneral()
        val edited = repository.cachedHostConfig()!!.copy(port = 8989 + 1)

        val result = repository.saveHostConfig(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(8990, api.hostConfig.port)
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
            SonarrSettingsSystemRepository.HOST_CONFIG_TYPE,
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
        assertEquals("api/v3/config/host/1", action.path)
        assertFalse(action.bodyJson!!.contains("typed-secret"))
        assertFalse(action.bodyJson!!.contains("existing-password"))
        assertFalse(action.bodyJson!!.contains("server-secret"))
        assertEquals(8321, repository.cachedHostConfig()!!.port)
        assertEquals(
            stampBefore,
            store.lastFetchedAt(serverId, SonarrSettingsSystemRepository.HOST_CONFIG_TYPE),
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
        assertEquals("Backup", commands.commandStore.values.single().name)
    }

    @Test
    fun `createBackup offline queues the command`() = runTest {
        commands.error = { IOException("offline") }
        val result = repository.createBackup()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson!!.contains("\"Backup\""))
    }

    @Test
    fun `restoreBackup succeeds and surfaces http errors`() = runTest {
        seed()
        val ok = repository.restoreBackup(4)
        assertTrue(ok is ArrResult.Ok)
        assertEquals(listOf(4), api.restoredBackupIds)

        api.error = { op -> if (op == "Backup.restore") HttpException(errorResponse(400)) else null }
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
        api.error = { IOException("offline") }
        val queued = repository.deleteBackup(6)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedBackups().isEmpty())
        assertEquals("api/v3/system/backup/6", queue.pending().single().path)
    }

    @Test
    fun `installUpdate dispatches the ApplicationUpdate command`() = runTest {
        val result = repository.installUpdate()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("ApplicationUpdate", commands.commandStore.values.single().name)
    }

    @Test
    fun `updateLogFileContents renders the passthrough body`() = runTest {
        api.updateLogFileContents = buildJsonObject { put("log", "2026-10-20 10:00:00|Info|Update installed") }
        val result = repository.updateLogFileContents("sonarr.update.txt")
        assertTrue((result as ArrResult.Ok).value.contains("Update installed"))
    }

    // ---------------------------------------------------------------- tag CRUD

    @Test
    fun `tag create and rename go through online`() = runTest {
        seed()
        val created = repository.saveTag(TagResource(label = "4k"))
        assertTrue((created as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(2, api.tagStore.size)
        assertEquals("4k", api.tagStore[2]!!.label)

        val renamed = repository.saveTag(TagResource(id = 2, label = "uhd"))
        assertTrue((renamed as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("uhd", api.tagStore[2]!!.label)
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
        assertEquals("api/v3/tag", queue.pending().single().path)
    }

    @Test
    fun `tag delete drops the row and details, online and offline`() = runTest {
        seed()
        repository.refreshTags()
        val synced = repository.deleteTag(1)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.tagStore.isEmpty())
        assertTrue(repository.cachedTags().isEmpty())
        assertTrue(repository.cachedTagDetails().isEmpty())

        api.tagStore[9] = TagResource(id = 9, label = "temp")
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
        val edited = api.autoTaggingStore.values.first().copy(name = "Renamed")
        val synced = repository.saveAutoTagging(edited)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Renamed", api.autoTaggingStore.values.first().name)

        api.error = { IOException("offline") }
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
        assertTrue(api.autoTaggingStore.isEmpty())

        api.autoTaggingStore[3] = AutoTaggingResource(id = 3, name = "Later")
        repository.refreshAutoTagging()
        api.error = { IOException("offline") }
        val queued = repository.deleteAutoTagging(3)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/autotagging/3", queue.pending().single().path)
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
        queue.enqueue(queue.action("other", path = "api/v3/series/editor"))
        queue.enqueue(
            queue.action("queue-owned", path = "api/v3/command").copy(description = "Series search"),
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
