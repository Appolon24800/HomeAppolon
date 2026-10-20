package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.api.bazarr.model.PlexSelectServerRequest
import dev.appolon.homelab.arr.api.bazarr.model.RadarrWebhook
import dev.appolon.homelab.arr.api.bazarr.model.SonarrWebhook
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** An invocation snapshot. Keys match the spec's query, path, and body parameter names. */
data class BazarrSubtitlesCall(val operationId: String, val arguments: Map<String, Any?>)

/**
 * In-memory fake for all subtitle-owned tags. Configure responses and statusCodes by operationId.
 * Calls capture arguments, including mutation payloads, so UI tests can inspect submitted state.
 * latency can call a test scheduler's delay without a runtime coroutine dependency here.
 * Configure and call this fake from one test dispatcher.
 */
class FakeBazarrSubtitles(
    var latency: suspend (String) -> Unit = {},
    var errorInjection: (String) -> Throwable? = { null },
) : SubtitlesApi, SubtitleContentsApi, SubtitlesInfoApi, ProvidersApi, ProvidersEpisodesApi,
    ProvidersMoviesApi, HistoryStatisticsApi, SystemSearchesApi, WebhooksPlexApi,
    WebhooksRadarrApi, WebhooksSonarrApi, PlexAuthenticationApi, JellyfinApi,
    FilesBrowserforBazarrApi, FilesBrowserforRadarrApi, FilesBrowserforSonarrApi {

    val responses: MutableMap<String, JsonObject> = mutableMapOf()
    val statusCodes: MutableMap<String, Int> = mutableMapOf()
    private val recordedCalls = mutableListOf<BazarrSubtitlesCall>()
    val calls: List<BazarrSubtitlesCall> get() = recordedCalls.toList()

    private suspend fun record(operationId: String, vararg args: Pair<String, Any?>) {
        recordedCalls += BazarrSubtitlesCall(operationId, args.toMap())
        latency(operationId)
        errorInjection(operationId)?.let { throw it }
    }

    private fun <T> response(operationId: String, body: T?, defaultCode: Int): Response<T> {
        val code = statusCodes[operationId] ?: defaultCode
        require(code in 200..599) { "Expected an HTTP status in 200..599" }
        return if (code < 300) {
            Response.success(code, if (code == 204 || code == 205) null else body)
        } else {
            Response.error(code, "{}".toResponseBody("application/json".toMediaType()))
        }
    }

    private suspend fun json(operationId: String, vararg args: Pair<String, Any?>): JsonObject {
        record(operationId, *args)
        val result = response(operationId, responses[operationId] ?: JsonObject(emptyMap()), 200)
        if (!result.isSuccessful) throw HttpException(result)
        return checkNotNull(result.body()) { "No JSON response for $operationId" }
    }

    private suspend fun empty(
        operationId: String,
        vararg args: Pair<String, Any?>,
    ): Response<Unit> {
        record(operationId, *args)
        return response(operationId, null, 204)
    }

    override suspend fun patchSubtitles(
        action: String,
        language: String,
        type: String,
        id: Int,
        path: String?,
        forced: String?,
        hi: String?,
        originalFormat: String?,
        reference: String?,
        maxOffsetSeconds: String?,
        noFixFramerate: String?,
        gss: String?,
        qualityMinScore: String?,
        qualityMaxOffsetSeconds: String?,
        qualityMaxFramerateDeviation: String?,
        subtitlesId: Int?,
    ): Response<Unit> = empty(
        "patch_subtitles", "action" to action, "language" to language, "type" to type,
        "id" to id, "path" to path, "forced" to forced, "hi" to hi,
        "original_format" to originalFormat, "reference" to reference,
        "max_offset_seconds" to maxOffsetSeconds, "no_fix_framerate" to noFixFramerate,
        "gss" to gss, "quality_min_score" to qualityMinScore,
        "quality_max_offset_seconds" to qualityMaxOffsetSeconds,
        "quality_max_framerate_deviation" to qualityMaxFramerateDeviation,
        "subtitles_id" to subtitlesId,
    )

    override suspend fun getSubtitles(
        subtitlesPath: String,
        sonarrEpisodeId: Int?,
        radarrMovieId: Int?,
    ): JsonObject = json(
        "get_subtitles", "subtitlesPath" to subtitlesPath,
        "sonarrEpisodeId" to sonarrEpisodeId, "radarrMovieId" to radarrMovieId,
    )

    override suspend fun getSubtitleNameContents(subtitlePath: String): JsonObject =
        json("get_subtitle_name_contents", "subtitlePath" to subtitlePath)

    override suspend fun getSubtitleNameInfo(filenames: List<String>): JsonObject =
        json("get_subtitle_name_info", "filenames[]" to filenames.toList())

    override suspend fun postProviders(action: String): Response<Unit> =
        empty("post_providers", "action" to action)

    override suspend fun getProviders(history: String?): JsonObject =
        json("get_providers", "history" to history)

    override suspend fun postProviderEpisodes(
        seriesid: Int,
        episodeid: Int,
        hi: String,
        forced: String,
        originalFormat: String,
        provider: String,
        subtitle: String,
    ): Response<Unit> = empty(
        "post_provider_episodes", "seriesid" to seriesid, "episodeid" to episodeid,
        "hi" to hi, "forced" to forced, "original_format" to originalFormat,
        "provider" to provider, "subtitle" to subtitle,
    )

    override suspend fun getProviderEpisodes(episodeid: Int): JsonObject =
        json("get_provider_episodes", "episodeid" to episodeid)

    override suspend fun postProviderMovies(
        radarrid: Int,
        hi: String,
        forced: String,
        originalFormat: String,
        provider: String,
        subtitle: String,
    ): Response<Unit> = empty(
        "post_provider_movies", "radarrid" to radarrid, "hi" to hi, "forced" to forced,
        "original_format" to originalFormat, "provider" to provider, "subtitle" to subtitle,
    )

    override suspend fun getProviderMovies(radarrid: Int): JsonObject =
        json("get_provider_movies", "radarrid" to radarrid)

    override suspend fun getHistoryStats(
        timeFrame: String?,
        action: String?,
        provider: String?,
        language: String?,
    ): JsonObject = json(
        "get_history_stats", "timeFrame" to timeFrame, "action" to action,
        "provider" to provider, "language" to language,
    )

    override suspend fun getSearches(query: String): JsonObject =
        json("get_searches", "query" to query)

    override suspend fun postWebHooksPlex(payload: String): Response<JsonObject> {
        val operationId = "post_web_hooks_plex"
        record(operationId, "payload" to payload)
        return response(operationId, responses[operationId] ?: JsonObject(emptyMap()), 200)
    }

    override suspend fun postWebHooksRadarr(payload: RadarrWebhook): JsonObject =
        json("post_web_hooks_radarr", "payload" to payload)

    override suspend fun postWebHooksSonarr(payload: SonarrWebhook): JsonObject =
        json("post_web_hooks_sonarr", "payload" to payload)

    override suspend fun postPlexApiKey(apikey: String): JsonObject =
        json("post_plex_api_key", "apikey" to apikey)

    override suspend fun getPlexAutopulseConfig(): JsonObject = json("get_plex_autopulse_config")
    override suspend fun postPlexEncryptApiKey(): JsonObject = json("post_plex_encrypt_api_key")
    override suspend fun getPlexLibraries(): JsonObject = json("get_plex_libraries")
    override suspend fun postPlexLogout(): JsonObject = json("post_plex_logout")
    override suspend fun postPlexPin(clientId: String?): JsonObject =
        json("post_plex_pin", "clientId" to clientId)
    override suspend fun getPlexPin(): JsonObject = json("get_plex_pin")
    override suspend fun getPlexPinCheck(pinId: String): JsonObject =
        json("get_plex_pin_check", "pin_id" to pinId)
    override suspend fun getPlexServers(): JsonObject = json("get_plex_servers")
    override suspend fun getPlexValidate(): JsonObject = json("get_plex_validate")

    override suspend fun postPlexSelectServer(
        machineIdentifier: String,
        name: String,
        uri: String,
        payload: PlexSelectServerRequest,
        local: String?,
    ): JsonObject = json(
        "post_plex_select_server", "machineIdentifier" to machineIdentifier, "name" to name,
        "uri" to uri, "payload" to payload, "local" to local,
    )

    override suspend fun getPlexSelectServer(): JsonObject = json("get_plex_select_server")
    override suspend fun postPlexTestConnection(uri: String): JsonObject =
        json("post_plex_test_connection", "uri" to uri)
    override suspend fun getPlexTestConnection(): JsonObject = json("get_plex_test_connection")
    override suspend fun postPlexWebhookCreate(): JsonObject = json("post_plex_webhook_create")
    override suspend fun postPlexWebhookDelete(webhookUrl: String): JsonObject =
        json("post_plex_webhook_delete", "webhook_url" to webhookUrl)
    override suspend fun getPlexWebhookList(): JsonObject = json("get_plex_webhook_list")

    override suspend fun getJellyfinLibraries(url: String?, apikey: String?): JsonObject =
        json("get_jellyfin_libraries", "url" to url, "apikey" to apikey)

    override suspend fun postJellyfinTestConnection(url: String, apikey: String): JsonObject =
        json("post_jellyfin_test_connection", "url" to url, "apikey" to apikey)

    override suspend fun getBrowseBazarrFs(path: String?): JsonObject =
        json("get_browse_bazarr_fs", "path" to path)
    override suspend fun getBrowseRadarrFs(path: String?): JsonObject =
        json("get_browse_radarr_fs", "path" to path)
    override suspend fun getBrowseSonarrFs(path: String?): JsonObject =
        json("get_browse_sonarr_fs", "path" to path)
}
