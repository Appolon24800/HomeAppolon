package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class FakeBazarrSystemTest {
    @Test
    fun seededResponsesKeepDatesAndUnknownFieldsUnchanged() = runTest {
        val fake = FakeBazarrSystem()
        val body = ArrJson.parseToJsonElement(
            """{"currentTime":"2030-01-02T03:04:05Z","future_field":[1,"unknown"]}"""
        ) as JsonObject
        fake.responses["get_system_status"] = body
        assertSame(body, fake.getSystemStatus())
        assertEquals("get_system_status", fake.calls.single().operation)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun latencyUsesTheTestDispatcherAndWritesCanMutateMemory() = runTest {
        val updated = ArrJson.parseToJsonElement("""{"changed":true}""") as JsonObject
        val fake = FakeBazarrSystem(
            latency = { delay(100) },
            mutate = { call, responses ->
                if (call.operation == "post_system_backups") {
                    responses["get_system_backups"] = updated
                }
            },
        )
        val start = testScheduler.currentTime
        assertEquals(204, fake.postSystemBackups().code())
        assertEquals("post_system_backups", fake.calls.single().operation)
        assertSame(updated, fake.getSystemBackups())
        assertEquals(200L, testScheduler.currentTime - start)
    }

    @Test
    fun injectedStatusUsesTheSameReadAndWriteSemanticsAsRetrofit() = runTest {
        var mutations = 0
        val fake = FakeBazarrSystem(
            errorStatus = { 409 },
            mutate = { _, _ -> mutations++ },
        )
        assertEquals(409, fake.patchSystemBackups("bazarr_v1_backup.zip").code())
        assertEquals(409, fake.postSystemTasks("sync-series").code())
        assertEquals(409, fake.postSystemWebhookTest().code())
        assertEquals(0, mutations)
        try {
            fake.getSystemStatus()
            throw AssertionError("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(409, error.code())
        }
    }

    @Test
    fun transportFailuresPropagateWithoutChangingState() = runTest {
        val expected = IOException("synthetic failure")
        val fake = FakeBazarrSystem(failure = { expected })
        try {
            fake.deleteSystemJobs(7)
            throw AssertionError("Expected IOException")
        } catch (error: IOException) {
            assertSame(expected, error)
        }
        assertTrue(fake.responses.isEmpty())
        assertEquals(1, fake.calls.size)
    }

    @Test
    fun requiredArgumentsAreCapturedVerbatimForAssertion() = runTest {
        val fake = FakeBazarrSystem()
        fake.postSystemJobs(7, "force_start")
        fake.patchSystemJobs("update_listeners")
        fake.getSystemJobs(7, "running")
        val arguments = fake.calls.map { it.arguments }
        assertEquals(7, arguments[0]["id"])
        assertEquals("force_start", arguments[0]["action"])
        assertEquals("update_listeners", arguments[1]["queueName"])
        assertEquals("running", arguments[2]["status"])
    }
}
