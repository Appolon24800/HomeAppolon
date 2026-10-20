package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** Raw JSON is intentional: the bundled spec declares no response schema. */
interface MoviesApi {
    @GET("movies")
    suspend fun getMovies(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
        @Query("radarrid[]") radarrIds: List<Int>? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("sort_order") sortOrder: String? = null,
        @Query("monitored") monitored: String? = null,
        @Query("profileid") profileId: String? = null,
        @Query("missing") missing: String? = null,
        @Query("audio_language") audioLanguage: String? = null,
        @Query("tags[]") tags: List<String>? = null,
    ): JsonObject

    @PATCH("movies")
    suspend fun patchMovies(
        @Query("radarrid") radarrId: Int? = null,
        @Query("action") action: String? = null,
    ): Response<Unit>

    @POST("movies")
    suspend fun postMovies(
        @Query("radarrid") radarrIds: List<Int>? = null,
        @Query("profileid") profileIds: List<String>? = null,
    ): Response<Unit>
}
