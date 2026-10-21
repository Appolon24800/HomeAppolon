package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** Raw JSON is intentional: the bundled spec declares no response schema. */
interface SeriesApi {
    @GET("api/series")
    suspend fun getSeries(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
        @Query("seriesid[]") seriesIds: List<Int>? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("sort_order") sortOrder: String? = null,
        @Query("monitored") monitored: String? = null,
        @Query("profileid") profileId: String? = null,
        @Query("missing") missing: String? = null,
        @Query("audio_language") audioLanguage: String? = null,
        @Query("tags[]") tags: List<String>? = null,
    ): JsonObject

    @PATCH("api/series")
    suspend fun patchSeries(
        @Query("seriesid") seriesId: Int? = null,
        @Query("action") action: String? = null,
    ): Response<Unit>

    @POST("api/series")
    suspend fun postSeries(
        @Query("seriesid") seriesIds: List<Int>? = null,
        @Query("profileid") profileIds: List<String>? = null,
    ): Response<Unit>
}
