package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

/** Subtitle tools. Responses without a schema stay as JSON rather than guessed DTOs. */
interface SubtitlesApi {
    @PATCH("api/subtitles")
    suspend fun patchSubtitles(
        @Query("action") action: String,
        @Query("language") language: String,
        @Query("type") type: String,
        @Query("id") id: Int,
        @Query("path") path: String? = null,
        @Query("forced") forced: String? = null,
        @Query("hi") hi: String? = null,
        @Query("original_format") originalFormat: String? = null,
        @Query("reference") reference: String? = null,
        @Query("max_offset_seconds") maxOffsetSeconds: String? = null,
        @Query("no_fix_framerate") noFixFramerate: String? = null,
        @Query("gss") gss: String? = null,
        @Query("quality_min_score") qualityMinScore: String? = null,
        @Query("quality_max_offset_seconds") qualityMaxOffsetSeconds: String? = null,
        @Query("quality_max_framerate_deviation") qualityMaxFramerateDeviation: String? = null,
        @Query("subtitles_id") subtitlesId: Int? = null,
    ): Response<Unit>

    @GET("api/subtitles")
    suspend fun getSubtitles(
        @Query("subtitlesPath") subtitlesPath: String,
        @Query("sonarrEpisodeId") sonarrEpisodeId: Int? = null,
        @Query("radarrMovieId") radarrMovieId: Int? = null,
    ): JsonObject
}
