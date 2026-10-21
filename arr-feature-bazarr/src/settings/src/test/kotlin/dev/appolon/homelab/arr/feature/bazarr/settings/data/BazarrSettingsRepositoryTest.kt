package dev.appolon.homelab.arr.feature.bazarr.settings.data

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemCall
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.settings.backupRow
import dev.appolon.homelab.arr.feature.bazarr.settings.jobRow
import dev.appolon.homelab.arr.feature.bazarr.settings.languageRow
import dev.appolon.homelab.arr.feature.bazarr.settings.logRow
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrJobRow
import dev.appolon.homelab.arr.feature.bazarr.settings.newSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.providerRow
import dev.appolon.homelab.arr.feature.bazarr.settings.settingsDataPayload
import dev.appolon.homelab.arr.feature.bazarr.settings.taskRow
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BazarrSettingsRepositoryTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var system: FakeBazarrSystem
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue

    /** Injects a transport failure for one operation id; every other call passes. */
    private fun failing(operation: String): (BazarrSystemCall) -> Throwable? =
        { call -> if (call.operation == operation) IOException() else null }

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(clock: () -> Long = { 1_000L }) = newSettingsRepository(
        system = system,
        subtitles = subtitles,
        store = store,
        queue = queue,
        io = main,
        now = clock,
    )

    private fun seed() {
        system = FakeBazarrSystem()
        subtitles = FakeBazarrSubtitles()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        system.responses["get_languages"] = settingsDataPayload(
            listOf(
                languageRow("en", "English", enabled = true),
                languageRow("de", "German"),
            ),
        )
        system.responses["get_system_tasks"] = settingsDataPayload(
            listOf(taskRow(jobId = "update_series", name = "Update Series")),
        )
        system.responses["get_system_jobs"] = settingsDataPayload(
            listOf(
                jobRow(id = 2, queueName = "search_missing_subtitle"),
                jobRow(id = 3, queueName = "upgrade_subtitle"),
            ),
        )
        system.responses["get_system_backups"] = settingsDataPayload(
            listOf(backupRow(filename = "bazarr_backup.zip")),
        )
        system.responses["get_system_logs"] = settingsDataPayload(
            listOf(logRow(message = "Imported subtitles")),
        )
        system.responses["get_system_status"] = buildJsonObject {
            put("data", buildJsonObject { put("version", "1.6.2") })
        }
        subtitles.responses["get_providers"] = settingsDataPayload(
            listOf(providerRow(provider = "opensubtitles")),
        )
    }

    @Test
    fun `languages cache first and skip the network while fresh`() = runTest(main) {
        seed()
        val repo = repository()

        repo.languages()
        val first = repo.observeLanguages().first()
        assertEquals(listOf("en", "de"), first.map { it.code2 })
        assertTrue(first.first().enabled)

        system.calls.clear()
        val result = repo.languages()

        assertTrue(result is ArrResult.Ok)
        assertEquals(0, system.calls.count { it.operation == "get_languages" })

        val refreshed = repo.languages(ttlMillis = BazarrSettingsRepository.FORCE_REFRESH)
        assertTrue(refreshed is ArrResult.Ok)
        assertEquals(1, system.calls.count { it.operation == "get_languages" })
    }

    @Test
    fun `a glitched languages payload keeps the previous cache`() = runTest(main) {
        seed()
        val repo = repository()
        repo.languages()

        system.responses["get_languages"] = buildJsonObject { }
        repo.languages(ttlMillis = BazarrSettingsRepository.FORCE_REFRESH)

        assertEquals(listOf("en", "de"), repo.observeLanguages().first().map { it.code2 })
    }

    @Test
    fun `provider reset syncs online`() = runTest(main) {
        seed()
        val repo = repository()

        val result = repo.resetProviders()

        assertTrue((result as ArrResult.Ok).value is SettingsWriteOutcome.Synced)
        val call = subtitles.calls.single { it.operationId == "post_providers" }
        assertEquals("reset", call.arguments["action"])
        assertFalse(repo.queuedProviderReset().first())
    }

    @Test
    fun `offline provider reset queues a replayable action`() = runTest(main) {
        seed()
        val repo = repository()
        subtitles.errorInjection = { if (it == "post_providers") IOException() else null }

        val result = repo.resetProviders()

        assertTrue((result as ArrResult.Ok).value is SettingsWriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/providers?action=reset", action.path)
        assertTrue(repo.queuedProviderReset().first())
    }

    @Test
    fun `an http failure is not queued`() = runTest(main) {
        seed()
        val repo = repository()
        system.errorStatus = { call -> if (call.operation == "delete_system_jobs") 500 else null }

        val result = repo.deleteJob(BazarrJobRow(id = 2))

        assertTrue(result is ArrResult.Err)
        assertTrue((result as ArrResult.Err).error is ArrError.Http)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `offline run now marks the task running and queues by task id`() = runTest(main) {
        seed()
        val repo = repository()
        repo.tasks()
        system.failure = failing("post_system_tasks")

        val result = repo.runTaskNow(repo.observeTasks().first().single())

        assertTrue((result as ArrResult.Ok).value is SettingsWriteOutcome.Queued)
        assertTrue(repo.observeTasks().first().single().running)
        assertEquals("api/system/tasks?taskid=update_series", queue.pending().single().path)
        assertEquals(setOf("update_series"), repo.queuedTaskIds().first())
    }

    @Test
    fun `online empty queue drops only that queue's rows`() = runTest(main) {
        seed()
        val repo = repository()
        repo.jobs()

        val result = repo.emptyJobQueue("search_missing_subtitle")

        assertTrue((result as ArrResult.Ok).value is SettingsWriteOutcome.Synced)
        assertEquals(listOf(3), repo.observeJobs().first().map { it.id })
        val call = system.calls.single { it.operation == "patch_system_jobs" }
        assertEquals("search_missing_subtitle", call.arguments["queueName"])
    }

    @Test
    fun `offline job delete drops the row optimistically and queues by id`() = runTest(main) {
        seed()
        val repo = repository()
        repo.jobs()
        system.failure = failing("delete_system_jobs")

        val result = repo.deleteJob(BazarrJobRow(id = 2, queueName = "search_missing_subtitle"))

        assertTrue((result as ArrResult.Ok).value is SettingsWriteOutcome.Queued)
        assertEquals(listOf(3), repo.observeJobs().first().map { it.id })
        assertEquals(setOf(2), repo.queuedJobIds().first())
    }

    @Test
    fun `offline backup delete queues by filename and drops the row`() = runTest(main) {
        seed()
        val repo = repository()
        repo.backups()
        system.failure = failing("delete_system_backups")

        val result = repo.deleteBackup(repo.observeBackups().first().single())

        assertTrue((result as ArrResult.Ok).value is SettingsWriteOutcome.Queued)
        assertTrue(repo.observeBackups().first().isEmpty())
        assertEquals(
            "api/system/backups?filename=bazarr_backup.zip",
            queue.pending().single().path,
        )
        assertEquals(setOf("bazarr_backup.zip"), repo.queuedBackupFiles().first())
    }

    @Test
    fun `log rotation clears the cached tail and can queue offline`() = runTest(main) {
        seed()
        val repo = repository()
        repo.logs()
        assertTrue(repo.observeLogs().first().isNotEmpty())

        val synced = repo.rotateLogs()

        assertTrue((synced as ArrResult.Ok).value is SettingsWriteOutcome.Synced)
        assertTrue(repo.observeLogs().first().isEmpty())

        system.failure = failing("delete_system_logs")
        val queued = repo.rotateLogs()

        assertTrue((queued as ArrResult.Ok).value is SettingsWriteOutcome.Queued)
        assertTrue(repo.queuedLogRotation().first())
    }

    @Test
    fun `status rows cache through the data wrapper`() = runTest(main) {
        seed()
        val repo = repository()

        val result = repo.status()

        assertTrue(result is ArrResult.Ok)
        assertEquals("1.6.2", repo.observeStatus().first().single().value)
    }

    @Test
    fun `webhook test reports errors without queueing`() = runTest(main) {
        seed()
        val repo = repository()
        system.failure = failing("post_system_webhook_test")

        val failed = repo.testWebhook()

        assertTrue((failed as ArrResult.Err).error is ArrError.Network)
        assertTrue(queue.pending().isEmpty())

        system.failure = { null }
        val ok = repo.testWebhook()

        assertTrue(ok is ArrResult.Ok)
        assertTrue(queue.pending().isEmpty())
    }
}
