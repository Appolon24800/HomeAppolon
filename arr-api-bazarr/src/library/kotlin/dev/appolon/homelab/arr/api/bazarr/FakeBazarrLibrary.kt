package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** A captured request retains repeated query values and uploaded parts without lossy conversion. */
data class BazarrLibraryCall(val operation: String, val arguments: Map<String, Any?>)

/**
 * In-memory scripted library for UI tests. Seed [responses] with raw spec-compatible objects.
 * [latency] is suspending so callers can supply coroutine delay without blocking a UI thread.
 * [errorStatus] injects HTTP errors, while [failure] injects transport or cancellation failures.
 * [mutate] runs after successful writes and can update [responses] for the next read.
 * Configure and use this fake from one test dispatcher.
 */
class FakeBazarrLibrary(
    var latency: suspend (BazarrLibraryCall) -> Unit = {},
    var errorStatus: (BazarrLibraryCall) -> Int? = { null },
    var failure: (BazarrLibraryCall) -> Throwable? = { null },
    var mutate: (BazarrLibraryCall, MutableMap<String, JsonObject>) -> Unit = { _, _ -> },
) : SeriesApi, SeriesTagsApi, EpisodesApi, EpisodesSubtitlesApi, EpisodesBlacklistApi,
    EpisodesHistoryApi, EpisodesWantedApi, MoviesApi, MoviesTagsApi, MoviesSubtitlesApi,
    MoviesBlacklistApi, MoviesHistoryApi, MoviesWantedApi, BadgesApi {

    val responses: MutableMap<String, JsonObject> = mutableMapOf()
    val calls: MutableList<BazarrLibraryCall> = mutableListOf()

    private suspend fun before(operation: String, args: Map<String, Any?>): Pair<
        BazarrLibraryCall, Int?
    > {
        val call = BazarrLibraryCall(operation, args)
        calls.add(call)
        latency(call)
        failure(call)?.let { throw it }
        val status = errorStatus(call)
        require(status == null || status in 400..599)
        return call to status
    }

    private suspend fun read(operation: String, vararg args: Pair<String, Any?>): JsonObject {
        val (_, status) = before(operation, args.toMap())
        if (status != null) {
            throw HttpException(Response.error<JsonObject>(status, "{}".toResponseBody()))
        }
        return responses[operation] ?: JsonObject(emptyMap())
    }

    private suspend fun write(operation: String, vararg args: Pair<String, Any?>): Response<Unit> {
        val (call, status) = before(operation, args.toMap())
        if (status != null) return Response.error(status, "{}".toResponseBody())
        mutate(call, responses)
        return Response.success(204, Unit)
    }

    private suspend fun blacklist(
        operation: String,
        vararg args: Pair<String, Any?>,
    ): Response<JsonObject> {
        val (call, status) = before(operation, args.toMap())
        if (status != null) return Response.error(status, "{}".toResponseBody())
        mutate(call, responses)
        return Response.success(responses[operation] ?: JsonObject(emptyMap()))
    }

    override suspend fun getSeries(
        start: Int?, length: Int?, seriesIds: List<Int>?, sortBy: String?, sortOrder: String?,
        monitored: String?, profileId: String?, missing: String?, audioLanguage: String?,
        tags: List<String>?,
    ) = read(
        "get_series", "start" to start, "length" to length, "seriesid[]" to seriesIds,
        "sort_by" to sortBy, "sort_order" to sortOrder, "monitored" to monitored,
        "profileid" to profileId, "missing" to missing, "audio_language" to audioLanguage,
        "tags[]" to tags,
    )

    override suspend fun patchSeries(seriesId: Int?, action: String?) =
        write("patch_series", "seriesid" to seriesId, "action" to action)

    override suspend fun postSeries(seriesIds: List<Int>?, profileIds: List<String>?) =
        write("post_series", "seriesid" to seriesIds, "profileid" to profileIds)

    override suspend fun getMovies(
        start: Int?, length: Int?, radarrIds: List<Int>?, sortBy: String?, sortOrder: String?,
        monitored: String?, profileId: String?, missing: String?, audioLanguage: String?,
        tags: List<String>?,
    ) = read(
        "get_movies", "start" to start, "length" to length, "radarrid[]" to radarrIds,
        "sort_by" to sortBy, "sort_order" to sortOrder, "monitored" to monitored,
        "profileid" to profileId, "missing" to missing, "audio_language" to audioLanguage,
        "tags[]" to tags,
    )

    override suspend fun patchMovies(radarrId: Int?, action: String?) =
        write("patch_movies", "radarrid" to radarrId, "action" to action)

    override suspend fun postMovies(radarrIds: List<Int>?, profileIds: List<String>?) =
        write("post_movies", "radarrid" to radarrIds, "profileid" to profileIds)

    override suspend fun getEpisodes(seriesIds: List<Int>?, episodeIds: List<Int>?) =
        read("get_episodes", "seriesid[]" to seriesIds, "episodeid[]" to episodeIds)

    override suspend fun getSeriesTags() = read("get_series_tags")
    override suspend fun getMoviesTags() = read("get_movies_tags")
    override suspend fun getBadges() = read("get_badges")

    override suspend fun getEpisodesHistory(start: Int?, length: Int?, episodeId: Int?) =
        read("get_episodes_history", "start" to start, "length" to length, "episodeid" to episodeId)

    override suspend fun getMoviesHistory(start: Int?, length: Int?, radarrId: Int?) =
        read("get_movies_history", "start" to start, "length" to length, "radarrid" to radarrId)

    override suspend fun getEpisodesWanted(start: Int?, length: Int?, episodeIds: List<Int>?) =
        read(
            "get_episodes_wanted", "start" to start, "length" to length, "episodeid[]" to episodeIds,
        )

    override suspend fun getMoviesWanted(start: Int?, length: Int?, radarrIds: List<Int>?) =
        read("get_movies_wanted", "start" to start, "length" to length, "radarrid[]" to radarrIds)

    override suspend fun getEpisodesBlacklist(start: Int?, length: Int?) =
        read("get_episodes_blacklist", "start" to start, "length" to length)

    override suspend fun getMoviesBlacklist(start: Int?, length: Int?) =
        read("get_movies_blacklist", "start" to start, "length" to length)

    override suspend fun postEpisodesBlacklist(
        seriesId: Int, episodeId: Int, provider: String, subsId: String, language: String,
        subtitlesPath: String,
    ) = blacklist(
        "post_episodes_blacklist", "seriesid" to seriesId, "episodeid" to episodeId,
        "provider" to provider, "subs_id" to subsId, "language" to language,
        "subtitles_path" to subtitlesPath,
    )

    override suspend fun postMoviesBlacklist(
        radarrId: Int, provider: String, subsId: String, language: String, subtitlesPath: String,
    ) = blacklist(
        "post_movies_blacklist", "radarrid" to radarrId, "provider" to provider,
        "subs_id" to subsId, "language" to language, "subtitles_path" to subtitlesPath,
    )

    override suspend fun deleteEpisodesBlacklist(all: String?, provider: String?, subsId: String?) =
        write(
            "delete_episodes_blacklist", "all" to all, "provider" to provider, "subs_id" to subsId,
        )

    override suspend fun deleteMoviesBlacklist(all: String?, provider: String?, subsId: String?) =
        write("delete_movies_blacklist", "all" to all, "provider" to provider, "subs_id" to subsId)

    override suspend fun patchEpisodesSubtitles(
        seriesId: Int, episodeId: Int, language: String, forced: String, hi: String,
    ) = write(
        "patch_episodes_subtitles", "seriesid" to seriesId, "episodeid" to episodeId,
        "language" to language, "forced" to forced, "hi" to hi,
    )

    override suspend fun postEpisodesSubtitles(
        seriesId: Int, episodeId: Int, language: String, forced: String, hi: String,
        file: MultipartBody.Part,
    ) = write(
        "post_episodes_subtitles", "seriesid" to seriesId, "episodeid" to episodeId,
        "language" to language, "forced" to forced, "hi" to hi, "file" to file,
    )

    override suspend fun deleteEpisodesSubtitles(
        seriesId: Int, episodeId: Int, language: String, forced: String, hi: String, path: String,
    ) = write(
        "delete_episodes_subtitles", "seriesid" to seriesId, "episodeid" to episodeId,
        "language" to language, "forced" to forced, "hi" to hi, "path" to path,
    )

    override suspend fun patchMoviesSubtitles(
        radarrId: Int, language: String, forced: String, hi: String,
    ) = write(
        "patch_movies_subtitles", "radarrid" to radarrId,
        "language" to language, "forced" to forced, "hi" to hi,
    )

    override suspend fun postMoviesSubtitles(
        radarrId: Int, language: String, forced: String, hi: String, file: MultipartBody.Part,
    ) = write(
        "post_movies_subtitles", "radarrid" to radarrId,
        "language" to language, "forced" to forced, "hi" to hi, "file" to file,
    )

    override suspend fun deleteMoviesSubtitles(
        radarrId: Int, language: String, forced: String, hi: String, path: String,
    ) = write(
        "delete_movies_subtitles", "radarrid" to radarrId,
        "language" to language, "forced" to forced, "hi" to hi, "path" to path,
    )
}
