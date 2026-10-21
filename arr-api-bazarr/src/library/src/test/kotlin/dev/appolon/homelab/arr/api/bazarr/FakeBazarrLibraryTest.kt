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

class FakeBazarrLibraryTest {
    @Test
    fun seededResponsesKeepDatesAndUnknownFieldsUnchanged() = runTest {
        val fake = FakeBazarrLibrary()
        val body = ArrJson.parseToJsonElement(
            """{"date":"2030-01-02T03:04:05Z","future_field":[1,"unknown"]}"""
        ) as JsonObject
        fake.responses["get_episodes_history"] = body
        assertSame(body, fake.getEpisodesHistory())
        assertEquals("get_episodes_history", fake.calls.single().operation)
    }

    @Test
    fun latencyUsesTheTestDispatcherAndWritesCanMutateMemory() = runTest {
        val updated = ArrJson.parseToJsonElement("""{"changed":true}""") as JsonObject
        val fake = FakeBazarrLibrary(
            latency = { delay(100) },
            mutate = { call, responses ->
                if (call.operation == "post_series") responses["get_series"] = updated
            },
        )
        val start = testScheduler.currentTime
        assertEquals(204, fake.postSeries(listOf(1, 2), listOf("none", "3")).code())
        assertEquals(listOf(1, 2), fake.calls.single().arguments["seriesid"])
        assertSame(updated, fake.getSeries())
        assertEquals(200L, testScheduler.currentTime - start)
    }

    @Test
    fun injectedStatusUsesTheSameReadAndWriteSemanticsAsRetrofit() = runTest {
        var mutations = 0
        val fake = FakeBazarrLibrary(
            errorStatus = { 409 },
            mutate = { _, _ -> mutations++ },
        )
        assertEquals(409, fake.patchMoviesSubtitles(1, "en", "false", "true").code())
        assertEquals(409, fake.postMoviesBlacklist(1, "p", "s", "en", "/s").code())
        assertEquals(0, mutations)
        try {
            fake.getBadges()
            throw AssertionError("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(409, error.code())
        }
    }

    @Test
    fun transportFailuresPropagateWithoutChangingState() = runTest {
        val expected = IOException("synthetic failure")
        val fake = FakeBazarrLibrary(failure = { expected })
        try {
            fake.patchSeries(1, "sync")
            throw AssertionError("Expected IOException")
        } catch (error: IOException) {
            assertSame(expected, error)
        }
        assertTrue(fake.responses.isEmpty())
        assertEquals(1, fake.calls.size)
    }
}
