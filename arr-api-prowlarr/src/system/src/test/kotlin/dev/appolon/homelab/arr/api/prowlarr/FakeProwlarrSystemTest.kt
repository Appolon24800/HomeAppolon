package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.BackupResource
import dev.appolon.homelab.arr.api.prowlarr.model.BackupType
import dev.appolon.homelab.arr.api.prowlarr.model.LogResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import dev.appolon.homelab.arr.api.prowlarr.model.UpdateResource
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** Drives the fake the way UI tests will: seeded state, injections, lifecycle. */
class FakeProwlarrSystemTest {
    @Test
    fun statusRoutesAndPingServeSeededState() = runTest {
        val fake = FakeProwlarrSystem()
        fake.status = SystemResource(appName = "Prowlarr", version = "1.0.0.4488")

        assertEquals("Prowlarr", fake.getSystemStatus().appName)
        assertEquals("1.0.0.4488", fake.getSystemStatus().version)
        assertEquals(fake.routesBody, fake.getSystemRoutes().string())
        assertEquals(fake.routesDuplicate, fake.getSystemRoutesDuplicate())
        assertEquals("OK", fake.ping().status)
        assertEquals(200, fake.pingHead().code())
        assertEquals("v1", fake.getApiInfo().current)
    }

    @Test
    fun logFiltersSortsAndPages() = runTest {
        val fake = FakeProwlarrSystem()
        fun line(id: Int, time: String, level: String) =
            LogResource(id = id, time = time, level = level, message = "line $id")
        fake.logs += listOf(
            line(1, "2026-10-20T08:00:00Z", "info"),
            line(2, "2026-10-20T07:59:58Z", "debug"),
            line(3, "2026-10-20T07:59:57Z", "error"),
            line(4, "2026-10-20T07:59:56Z", "info"),
        )

        val page = fake.getLog(2, 1, "time", SortDirection.DESCENDING, null)
        assertEquals(2, page.page)
        assertEquals(1, page.pageSize)
        assertEquals(4, page.totalRecords)
        assertEquals(listOf(2), page.records?.map { it.id })

        assertEquals(
            listOf(1, 4),
            fake.getLog(1, 10, null, null, "INFO").records?.map { it.id },
        )
    }

    @Test
    fun backupLifecycleRestoresAndDeletes() = runTest {
        val fake = FakeProwlarrSystem()
        fake.backups += listOf(
            BackupResource(id = 3, name = "spec.zip", type = BackupType.MANUAL),
        )

        assertEquals(1, fake.getBackups().size)
        assertTrue(fake.restoreBackup(3).isSuccessful)
        assertTrue(fake.deleteBackup(3).isSuccessful)
        assertTrue(fake.getBackups().isEmpty())
    }

    @Test
    fun unknownIdsAndBackupsThrow404() = runTest {
        val fake = FakeProwlarrSystem()
        fake.tasks[1] = TaskResource(id = 1, name = "Backup")

        expectHttpException(404) { fake.getTask(9) }
        expectHttpException(404) { fake.restoreBackup(9) }
        expectHttpException(404) { fake.deleteBackup(9) }
        assertEquals("Backup", fake.getTask(1).name)
    }

    @Test
    fun shutdownAndRestartFlipFlagsOnceAccepted() = runTest {
        val fake = FakeProwlarrSystem()

        assertFalse(fake.shutdownRequested)
        assertFalse(fake.restartRequested)
        assertTrue(fake.shutdown().isSuccessful)
        assertTrue(fake.restart().isSuccessful)
        assertTrue(fake.shutdownRequested)
        assertTrue(fake.restartRequested)
    }

    @Test
    fun updatesTasksHealthAndLogFileBodiesServeSeededState() = runTest {
        val fake = FakeProwlarrSystem()
        fake.updates += UpdateResource(id = 1, version = "1.1.0.4500", latest = true)
        fake.tasks[2] = TaskResource(id = 2, name = "Check For Updates")
        fake.logFileBody = "spec log body"

        assertEquals("1.1.0.4500", fake.getUpdates().single().version)
        assertEquals(listOf(2), fake.getTasks().map { it.id })
        assertTrue(fake.getHealth().isEmpty())
        assertTrue(fake.getLogFiles().isEmpty())
        assertTrue(fake.getUpdateLogFiles().isEmpty())
        assertEquals("spec log body", fake.getLogFileContents("prowlarr.txt").string())
        assertEquals(
            "spec log body",
            fake.getUpdateLogFileContents("prowlarr.update.2026-09-01.txt").string(),
        )
    }

    @Test
    fun staticRoutesServeSeededPageAndFreshBodies() = runTest {
        val fake = FakeProwlarrSystem()
        fake.staticBody = "<html>spec</html>"

        val login = fake.getLoginPage()
        assertTrue(login.isSuccessful)
        assertEquals(fake.staticBody, requireNotNull(login.body()).string())
        assertEquals(fake.staticBody, requireNotNull(fake.getRoot().body()).string())
        assertEquals(fake.staticBody, requireNotNull(fake.getContent("favicon.ico").body()).string())
        assertEquals(fake.staticBody, requireNotNull(fake.getStaticResource("index.html").body()).string())
    }

    @Test
    fun injectionsGateEveryCall() = runTest {
        val fake = FakeProwlarrSystem()
        fake.errorStatus = { call ->
            when (call.operation) {
                "getSystemStatus" -> 503
                "shutdown" -> 401
                "pingHead" -> 500
                else -> null
            }
        }

        expectHttpException(503) { fake.getSystemStatus() }
        assertEquals(401, fake.shutdown().code())
        assertEquals(500, fake.pingHead().code())

        val failing = FakeProwlarrSystem(
            failure = { call -> if (call.operation == "ping") IOException("offline") else null },
        )
        failing.errorStatus = { 503 }
        expectHttpException(503) { failing.getSystemStatus() }

        var observed = 0
        val slow = FakeProwlarrSystem(latency = { delay(1); observed++ })
        slow.ping()
        assertEquals(1, observed)
    }

    @Test
    fun callsRecordWireShapedArguments() = runTest {
        val fake = FakeProwlarrSystem()
        fake.backups += BackupResource(id = 3)

        fake.getSystemStatus()
        fake.getLog(2, 5, "time", SortDirection.DESCENDING, "info")
        fake.deleteBackup(3)
        fake.ping()

        assertEquals(
            listOf("getSystemStatus", "getLog", "deleteBackup", "ping"),
            fake.calls.map { it.operation },
        )
        assertEquals(3, fake.calls.single { it.operation == "deleteBackup" }.arguments["id"])
        assertEquals(
            listOf("page", "pageSize", "sortKey", "sortDirection", "level"),
            fake.calls.single { it.operation == "getLog" }.arguments.keys.toList(),
        )
    }

    private suspend fun expectHttpException(status: Int, block: suspend () -> Any?) {
        try {
            block()
            throw AssertionError("Expected HttpException with status $status")
        } catch (error: HttpException) {
            assertEquals(status, error.code())
        }
    }
}
