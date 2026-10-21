package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.api.bazarr.model.PlexSelectServerRequest
import dev.appolon.homelab.arr.api.bazarr.model.RadarrMovie
import dev.appolon.homelab.arr.api.bazarr.model.RadarrMovieFile
import dev.appolon.homelab.arr.api.bazarr.model.RadarrWebhook
import dev.appolon.homelab.arr.api.bazarr.model.SonarrEpisode
import dev.appolon.homelab.arr.api.bazarr.model.SonarrEpisodeFile
import dev.appolon.homelab.arr.api.bazarr.model.SonarrWebhook
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class BazarrSubtitlesBehaviorTest {
    @Test
    fun absentOptionalQueriesUseServerDefaults() = runTest {
        MockWebServer().use { server ->
            server.start()
            val apis = SubtitleTestApis(retrofit = subtitleRetrofit(server))
            val invocations: List<suspend () -> Any> = listOf(
                { apis.subtitles.getSubtitles("/media/a.srt") },
                { apis.subtitles.patchSubtitles("extract", "en", "movie", 1) },
                { apis.providers.getProviders() },
                { apis.statistics.getHistoryStats() },
                { apis.plex.postPlexPin() },
                {
                    apis.plex.postPlexSelectServer(
                        "id", "Example", "uri", PlexSelectServerRequest(),
                    )
                },
                { apis.jellyfin.getJellyfinLibraries() },
                { apis.bazarrFiles.getBrowseBazarrFs() },
                { apis.radarrFiles.getBrowseRadarrFs() },
                { apis.sonarrFiles.getBrowseSonarrFs() },
            )
            val expectedKeys = listOf(
                setOf("subtitlesPath"), setOf("action", "language", "type", "id"),
                emptySet(), emptySet(), emptySet(), setOf("machineIdentifier", "name", "uri"),
                emptySet(), emptySet(), emptySet(), emptySet(),
            )
            invocations.forEachIndexed { index, invocation ->
                server.enqueue(MockResponse().setBody("{}"))
                invocation()
                val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                val queryKeys = checkNotNull(request.requestUrl).queryParameterNames
                assertEquals(expectedKeys[index], queryKeys)
                if (index == 5) assertEquals("{}", request.body.readUtf8())
            }
        }
    }

    @Test
    fun plexIgnoredEventPreserves204() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(204))
            val api = BazarrSubtitlesApis.webhooksPlex(subtitleRetrofit(server))
            assertIgnored(api.postWebHooksPlex("{}"))
        }
        val fake = FakeBazarrSubtitles()
        fake.statusCodes["post_web_hooks_plex"] = 204
        assertIgnored(fake.postWebHooksPlex("{}"))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun fakeLatencyUsesVirtualTimeAndDoesNotBlock() = runTest {
        val fake = FakeBazarrSubtitles(latency = { delay(250) })
        val pending = async { fake.getProviderMovies(1) }
        runCurrent()
        assertFalse(pending.isCompleted)
        assertEquals("get_provider_movies", fake.calls.single().operationId)
        advanceTimeBy(250)
        runCurrent()
        assertTrue(pending.isCompleted)
        assertEquals(JsonObject(emptyMap()), pending.await())
    }

    @Test
    fun fakeInjectsAndRecoversFromTransportFailure() = runTest {
        val failure = IOException("Synthetic connection failure")
        val fake = FakeBazarrSubtitles(errorInjection = { failure })
        try {
            fake.getProviderMovies(1)
            fail("Expected injected failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        fake.errorInjection = { null }
        assertEquals(JsonObject(emptyMap()), fake.getProviderMovies(1))
        assertEquals(2, fake.calls.size)
    }

    @Test
    fun unknownFieldsAndDateStringsPassThroughUnchanged() = runTest {
        val body = """{"future":{"field":true},"date":"2026-10-20T08:09:10+05:30"}"""
        val expected = ArrJson.decodeFromString<JsonObject>(body)
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(body))
            val api = BazarrSubtitlesApis.providers(subtitleRetrofit(server))
            assertEquals(expected, api.getProviders())
        }
        val fake = FakeBazarrSubtitles()
        fake.responses["get_providers"] = expected
        assertEquals(expected, fake.getProviders())
    }

    @Test
    fun optionalRequestFieldsAreAbsentAndFutureEventTypesRemainStrings() {
        val radarr = ArrJson.decodeFromString<RadarrWebhook>(
            """{"eventType":"FutureEvent","extra":true}""",
        )
        assertEquals(RadarrWebhook("FutureEvent"), radarr)
        assertNull(radarr.movie)
        assertNull(radarr.movieFile)
        assertEquals(
            """{"eventType":"FutureEvent"}""",
            ArrJson.encodeToString(RadarrWebhook.serializer(), radarr),
        )
        val sonarr = ArrJson.decodeFromString<SonarrWebhook>("""{"eventType":"Test"}""")
        assertEquals(SonarrWebhook("Test"), sonarr)
        assertNull(sonarr.episodes)
        assertNull(sonarr.episodeFiles)
        assertEquals(
            PlexSelectServerRequest(), ArrJson.decodeFromString<PlexSelectServerRequest>("{}"),
        )
    }

    @Test
    fun requiredWebhookPropertiesDoNotGetInventedDefaults() {
        val decoders: List<() -> Any> = listOf(
            { ArrJson.decodeFromString<RadarrWebhook>("{}") },
            { ArrJson.decodeFromString<SonarrWebhook>("{}") },
            { ArrJson.decodeFromString<RadarrMovie>("{}") },
            { ArrJson.decodeFromString<RadarrMovieFile>("{}") },
            { ArrJson.decodeFromString<SonarrEpisode>("{}") },
            { ArrJson.decodeFromString<SonarrEpisodeFile>("{}") },
        )
        decoders.forEach { decode ->
            try {
                decode()
                fail("Expected missing required property failure")
            } catch (_: SerializationException) {
                // Required fields must fail decoding rather than silently become id 0.
            }
        }
    }

    private fun assertIgnored(response: Response<JsonObject>) {
        assertEquals(204, response.code())
        assertNull(response.body())
    }
}
