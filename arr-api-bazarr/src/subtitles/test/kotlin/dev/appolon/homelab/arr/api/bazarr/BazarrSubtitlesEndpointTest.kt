package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.api.bazarr.model.PlexSelectServerRequest
import dev.appolon.homelab.arr.api.bazarr.model.RadarrWebhook
import dev.appolon.homelab.arr.api.bazarr.model.SonarrWebhook
import dev.appolon.homelab.arr.net.ArrJson
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun subtitleFixture(tag: String, file: String = "success.json"): String {
    val path = "fixtures/bazarr/$tag/$file"
    val stream = checkNotNull(BazarrSubtitlesEndpointTest::class.java.classLoader)
        .getResourceAsStream(path) ?: error("Missing fixture $path")
    return stream.bufferedReader().use { it.readText() }
}

internal fun subtitleRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/deployment/bazarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Runs the same invocation against Retrofit and the fake without reflection. */
class SubtitleTestApis(retrofit: Retrofit? = null, fake: FakeBazarrSubtitles? = null) {
    val subtitles: SubtitlesApi = retrofit?.let(BazarrSubtitlesApis::subtitles)
        ?: requireNotNull(fake)
    val contents: SubtitleContentsApi = retrofit?.let(BazarrSubtitlesApis::subtitleContents)
        ?: requireNotNull(fake)
    val info: SubtitlesInfoApi = retrofit?.let(BazarrSubtitlesApis::subtitlesInfo)
        ?: requireNotNull(fake)
    val providers: ProvidersApi = retrofit?.let(BazarrSubtitlesApis::providers)
        ?: requireNotNull(fake)
    val episodes: ProvidersEpisodesApi = retrofit?.let(BazarrSubtitlesApis::providersEpisodes)
        ?: requireNotNull(fake)
    val movies: ProvidersMoviesApi = retrofit?.let(BazarrSubtitlesApis::providersMovies)
        ?: requireNotNull(fake)
    val statistics: HistoryStatisticsApi = retrofit?.let(BazarrSubtitlesApis::historyStatistics)
        ?: requireNotNull(fake)
    val searches: SystemSearchesApi = retrofit?.let(BazarrSubtitlesApis::systemSearches)
        ?: requireNotNull(fake)
    val plexWebhook: WebhooksPlexApi = retrofit?.let(BazarrSubtitlesApis::webhooksPlex)
        ?: requireNotNull(fake)
    val radarrWebhook: WebhooksRadarrApi = retrofit?.let(BazarrSubtitlesApis::webhooksRadarr)
        ?: requireNotNull(fake)
    val sonarrWebhook: WebhooksSonarrApi = retrofit?.let(BazarrSubtitlesApis::webhooksSonarr)
        ?: requireNotNull(fake)
    val plex: PlexAuthenticationApi = retrofit?.let(BazarrSubtitlesApis::plexAuthentication)
        ?: requireNotNull(fake)
    val jellyfin: JellyfinApi = retrofit?.let(BazarrSubtitlesApis::jellyfin)
        ?: requireNotNull(fake)
    val bazarrFiles: FilesBrowserforBazarrApi =
        retrofit?.let(BazarrSubtitlesApis::filesBrowserforBazarr) ?: requireNotNull(fake)
    val radarrFiles: FilesBrowserforRadarrApi =
        retrofit?.let(BazarrSubtitlesApis::filesBrowserforRadarr) ?: requireNotNull(fake)
    val sonarrFiles: FilesBrowserforSonarrApi =
        retrofit?.let(BazarrSubtitlesApis::filesBrowserforSonarr) ?: requireNotNull(fake)
}

data class SubtitleEndpointCase(
    val operationId: String,
    val tag: String,
    val method: String,
    val path: String,
    val query: Map<String, List<String>> = emptyMap(),
    val bodyFixture: String? = null,
    val status: Int = 200,
    val returnsResponse: Boolean = status == 204,
    val invoke: suspend (SubtitleTestApis) -> Any,
) {
    override fun toString(): String = operationId
}

@RunWith(Parameterized::class)
class BazarrSubtitlesEndpointTest(private val case: SubtitleEndpointCase) {
    private lateinit var server: MockWebServer
    private lateinit var apis: SubtitleTestApis

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apis = SubtitleTestApis(retrofit = subtitleRetrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun endpointHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(case.status).apply {
            if (case.status != 204) {
                setHeader("Content-Type", "application/json")
                setBody(subtitleFixture(case.tag))
            }
        })
        assertSuccess(case.invoke(apis))
        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(case.method, request.method)
        val url = checkNotNull(request.requestUrl)
        assertEquals("/deployment/bazarr/api/${case.path}", url.encodedPath)
        assertEquals(case.query.keys, url.queryParameterNames)
        case.query.forEach { (key, values) ->
            assertEquals(values, url.queryParameterValues(key))
        }
        if (case.bodyFixture != null) {
            assertEquals(
                ArrJson.parseToJsonElement(subtitleFixture(case.tag, case.bodyFixture)),
                ArrJson.parseToJsonElement(request.body.readUtf8()),
            )
        } else {
            assertEquals(0L, request.bodySize)
        }
    }

    @Test
    fun endpointHttpFailure() = runTest {
        // Plex auth has no documented failure codes. A gateway 500 must still reach callers.
        val code = if (case.tag == "plex-authentication") 500 else 401
        server.enqueue(
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json")
                .setBody(subtitleFixture(case.tag, "error.json")),
        )
        if (case.returnsResponse) {
            val response = case.invoke(apis) as Response<*>
            assertEquals(code, response.code())
            assertNotNull(response.errorBody())
        } else {
            try {
                case.invoke(apis)
                fail("Expected HttpException")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
            }
        }
    }

    @Test
    fun fakeImplementsEndpointAndCapturesArguments() = runTest {
        val fake = FakeBazarrSubtitles()
        val fakeApis = SubtitleTestApis(fake = fake)
        fake.responses[case.operationId] = ArrJson.decodeFromString(subtitleFixture(case.tag))
        assertSuccess(case.invoke(fakeApis))
        val call = fake.calls.single()
        assertEquals(case.operationId, call.operationId)
        val queryArguments = call.arguments.filter { (name, value) ->
            value != null && name != "pin_id" && (name != "payload" || case.bodyFixture == null)
        }.mapValues { (_, value) ->
            if (value is List<*>) value.map { it.toString() } else listOf(value.toString())
        }
        assertEquals(case.query, queryArguments)
        if (case.operationId == "get_plex_pin_check") {
            assertEquals("pin/1", call.arguments["pin_id"])
        }
        case.bodyFixture?.let { fixture ->
            val body = when (val payload = call.arguments["payload"]) {
                is RadarrWebhook -> ArrJson.encodeToString(RadarrWebhook.serializer(), payload)
                is SonarrWebhook -> ArrJson.encodeToString(SonarrWebhook.serializer(), payload)
                is PlexSelectServerRequest ->
                    ArrJson.encodeToString(PlexSelectServerRequest.serializer(), payload)
                else -> error("Missing request DTO")
            }
            assertEquals(
                ArrJson.parseToJsonElement(subtitleFixture(case.tag, fixture)),
                ArrJson.parseToJsonElement(body),
            )
        }
        fake.statusCodes[case.operationId] = 401
        if (case.returnsResponse) {
            assertEquals(401, (case.invoke(fakeApis) as Response<*>).code())
        } else {
            try {
                case.invoke(fakeApis)
                fail("Expected fake HttpException")
            } catch (error: HttpException) {
                assertEquals(401, error.code())
            }
        }
    }

    private fun assertSuccess(result: Any) {
        if (case.returnsResponse) {
            val response = result as Response<*>
            assertEquals(case.status, response.code())
            if (case.status == 204) assertNull(response.body())
            else assertEquals(ArrJson.parseToJsonElement(subtitleFixture(case.tag)), response.body())
        } else {
            assertEquals(ArrJson.parseToJsonElement(subtitleFixture(case.tag)), result)
        }
    }

    companion object {
        private fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
            pairs.associate { (key, value) -> key to listOf(value) }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = endpoints().map { arrayOf(it) }

        private fun endpoints(): List<SubtitleEndpointCase> = listOf(
            SubtitleEndpointCase(
                "patch_subtitles", "subtitles", "PATCH", "subtitles",
                query(
                    "action" to "sync", "language" to "en", "type" to "episode", "id" to "1",
                    "path" to "/media/a & b.srt", "forced" to "False", "hi" to "True",
                    "original_format" to "False", "reference" to "a:0",
                    "max_offset_seconds" to "60", "no_fix_framerate" to "True", "gss" to "False",
                    "quality_min_score" to "0.5", "quality_max_offset_seconds" to "20",
                    "quality_max_framerate_deviation" to "0.1", "subtitles_id" to "2",
                ), status = 204,
            ) {
                it.subtitles.patchSubtitles(
                    "sync", "en", "episode", 1, "/media/a & b.srt", "False", "True", "False",
                    "a:0", "60", "True", "False", "0.5", "20", "0.1", 2,
                )
            },
            SubtitleEndpointCase(
                "get_subtitles", "subtitles", "GET", "subtitles",
                query("subtitlesPath" to "/media/a.srt", "sonarrEpisodeId" to "1",
                    "radarrMovieId" to "2"),
            ) { it.subtitles.getSubtitles("/media/a.srt", 1, 2) },
            SubtitleEndpointCase(
                "get_subtitle_name_contents", "subtitle-contents", "GET", "subtitles/contents",
                query("subtitlePath" to "/media/a.srt"),
            ) { it.contents.getSubtitleNameContents("/media/a.srt") },
            SubtitleEndpointCase(
                "get_subtitle_name_info", "subtitles-info", "GET", "subtitles/info",
                mapOf("filenames[]" to listOf("Example.en.srt", "Example.fr.srt")),
            ) { it.info.getSubtitleNameInfo(listOf("Example.en.srt", "Example.fr.srt")) },
            SubtitleEndpointCase(
                "post_providers", "providers", "POST", "providers",
                query("action" to "reset"), status = 204,
            ) { it.providers.postProviders("reset") },
            SubtitleEndpointCase(
                "get_providers", "providers", "GET", "providers", query("history" to "example"),
            ) { it.providers.getProviders("example") },
            SubtitleEndpointCase(
                "post_provider_episodes", "providers-episodes", "POST", "providers/episodes",
                query(
                    "seriesid" to "1", "episodeid" to "2", "hi" to "True", "forced" to "False",
                    "original_format" to "True", "provider" to "example", "subtitle" to "sub-1",
                ), status = 204,
            ) {
                it.episodes.postProviderEpisodes(1, 2, "True", "False", "True", "example", "sub-1")
            },
            SubtitleEndpointCase(
                "get_provider_episodes", "providers-episodes", "GET", "providers/episodes",
                query("episodeid" to "2"),
            ) { it.episodes.getProviderEpisodes(2) },
            SubtitleEndpointCase(
                "post_provider_movies", "providers-movies", "POST", "providers/movies",
                query(
                    "radarrid" to "1", "hi" to "True", "forced" to "False",
                    "original_format" to "True", "provider" to "example", "subtitle" to "sub-1",
                ), status = 204,
            ) { it.movies.postProviderMovies(1, "True", "False", "True", "example", "sub-1") },
            SubtitleEndpointCase(
                "get_provider_movies", "providers-movies", "GET", "providers/movies",
                query("radarrid" to "1"),
            ) { it.movies.getProviderMovies(1) },
            SubtitleEndpointCase(
                "get_history_stats", "history-statistics", "GET", "history/stats",
                query("timeFrame" to "week", "action" to "All", "provider" to "All",
                    "language" to "All"),
            ) { it.statistics.getHistoryStats("week", "All", "All", "All") },
            SubtitleEndpointCase(
                "get_searches", "system-searches", "GET", "system/searches",
                query("query" to "Example & sequel"),
            ) { it.searches.getSearches("Example & sequel") },
            SubtitleEndpointCase(
                "post_web_hooks_plex", "webhooks-plex", "POST", "webhooks/plex",
                query("payload" to "{}"), returnsResponse = true,
            ) { it.plexWebhook.postWebHooksPlex("{}") },
            SubtitleEndpointCase(
                "post_web_hooks_radarr", "webhooks-radarr", "POST", "webhooks/radarr",
                bodyFixture = "request.json",
            ) {
                it.radarrWebhook.postWebHooksRadarr(
                    ArrJson.decodeFromString(subtitleFixture("webhooks-radarr", "request.json")),
                )
            },
            SubtitleEndpointCase(
                "post_web_hooks_sonarr", "webhooks-sonarr", "POST", "webhooks/sonarr",
                bodyFixture = "request.json",
            ) {
                it.sonarrWebhook.postWebHooksSonarr(
                    ArrJson.decodeFromString(subtitleFixture("webhooks-sonarr", "request.json")),
                )
            },
            SubtitleEndpointCase(
                "post_plex_api_key", "plex-authentication", "POST", "plex/apikey",
                query("apikey" to "fixture-value"),
            ) { it.plex.postPlexApiKey("fixture-value") },
            SubtitleEndpointCase(
                "get_plex_autopulse_config", "plex-authentication", "GET", "plex/autopulse/config",
            ) { it.plex.getPlexAutopulseConfig() },
            SubtitleEndpointCase(
                "post_plex_encrypt_api_key", "plex-authentication", "POST", "plex/encrypt-apikey",
            ) { it.plex.postPlexEncryptApiKey() },
            SubtitleEndpointCase(
                "get_plex_libraries", "plex-authentication", "GET", "plex/oauth/libraries",
            ) { it.plex.getPlexLibraries() },
            SubtitleEndpointCase(
                "post_plex_logout", "plex-authentication", "POST", "plex/oauth/logout",
            ) { it.plex.postPlexLogout() },
            SubtitleEndpointCase(
                "post_plex_pin", "plex-authentication", "POST", "plex/oauth/pin",
                query("clientId" to "fixture-client"),
            ) { it.plex.postPlexPin("fixture-client") },
            SubtitleEndpointCase(
                "get_plex_pin", "plex-authentication", "GET", "plex/oauth/pin",
            ) { it.plex.getPlexPin() },
            SubtitleEndpointCase(
                "get_plex_pin_check", "plex-authentication", "GET", "plex/oauth/pin/pin%2F1/check",
            ) { it.plex.getPlexPinCheck("pin/1") },
            SubtitleEndpointCase(
                "get_plex_servers", "plex-authentication", "GET", "plex/oauth/servers",
            ) { it.plex.getPlexServers() },
            SubtitleEndpointCase(
                "get_plex_validate", "plex-authentication", "GET", "plex/oauth/validate",
            ) { it.plex.getPlexValidate() },
            SubtitleEndpointCase(
                "post_plex_select_server", "plex-authentication", "POST", "plex/select-server",
                query(
                    "machineIdentifier" to "fixture-id", "name" to "Example",
                    "uri" to "https://example.test:32400/", "local" to "false",
                ), bodyFixture = "select-server-request.json",
            ) {
                it.plex.postPlexSelectServer(
                    "fixture-id", "Example", "https://example.test:32400/",
                    ArrJson.decodeFromString(
                        subtitleFixture("plex-authentication", "select-server-request.json"),
                    ), "false",
                )
            },
            SubtitleEndpointCase(
                "get_plex_select_server", "plex-authentication", "GET", "plex/select-server",
            ) { it.plex.getPlexSelectServer() },
            SubtitleEndpointCase(
                "post_plex_test_connection", "plex-authentication", "POST", "plex/test-connection",
                query("uri" to "https://example.test:32400/"),
            ) { it.plex.postPlexTestConnection("https://example.test:32400/") },
            SubtitleEndpointCase(
                "get_plex_test_connection", "plex-authentication", "GET", "plex/test-connection",
            ) { it.plex.getPlexTestConnection() },
            SubtitleEndpointCase(
                "post_plex_webhook_create", "plex-authentication", "POST", "plex/webhook/create",
            ) { it.plex.postPlexWebhookCreate() },
            SubtitleEndpointCase(
                "post_plex_webhook_delete", "plex-authentication", "POST", "plex/webhook/delete",
                query("webhook_url" to "https://example.test/hook?a=1&b=2"),
            ) { it.plex.postPlexWebhookDelete("https://example.test/hook?a=1&b=2") },
            SubtitleEndpointCase(
                "get_plex_webhook_list", "plex-authentication", "GET", "plex/webhook/list",
            ) { it.plex.getPlexWebhookList() },
            SubtitleEndpointCase(
                "get_jellyfin_libraries", "jellyfin", "GET", "jellyfin/libraries",
                query("url" to "https://example.test/jellyfin/", "apikey" to "fixture-value"),
            ) { it.jellyfin.getJellyfinLibraries("https://example.test/jellyfin/", "fixture-value") },
            SubtitleEndpointCase(
                "post_jellyfin_test_connection", "jellyfin", "POST", "jellyfin/test-connection",
                query("url" to "https://example.test/jellyfin/", "apikey" to "fixture-value"),
            ) {
                it.jellyfin.postJellyfinTestConnection(
                    "https://example.test/jellyfin/", "fixture-value",
                )
            },
            SubtitleEndpointCase(
                "get_browse_bazarr_fs", "files-browser-for-bazarr", "GET", "files",
                query("path" to "/media/a & b/"),
            ) { it.bazarrFiles.getBrowseBazarrFs("/media/a & b/") },
            SubtitleEndpointCase(
                "get_browse_radarr_fs", "files-browser-for-radarr", "GET", "files/radarr",
                query("path" to "/media/movies/"),
            ) { it.radarrFiles.getBrowseRadarrFs("/media/movies/") },
            SubtitleEndpointCase(
                "get_browse_sonarr_fs", "files-browser-for-sonarr", "GET", "files/sonarr",
                query("path" to "/media/series/"),
            ) { it.sonarrFiles.getBrowseSonarrFs("/media/series/") },
        )
    }
}
