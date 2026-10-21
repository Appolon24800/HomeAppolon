package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Writes expose HTTP status; the spec leaves successful POST and GET bodies unspecified. */
interface EpisodesBlacklistApi {
    @GET("api/episodes/blacklist")
    suspend fun getEpisodesBlacklist(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
    ): JsonObject

    @POST("api/episodes/blacklist")
    suspend fun postEpisodesBlacklist(
        @Query("seriesid") seriesId: Int,
        @Query("episodeid") episodeId: Int,
        @Query("provider") provider: String,
        @Query("subs_id") subsId: String,
        @Query("language") language: String,
        @Query("subtitles_path") subtitlesPath: String,
    ): Response<JsonObject>

    @DELETE("api/episodes/blacklist")
    suspend fun deleteEpisodesBlacklist(
        @Query("all") all: String? = null,
        @Query("provider") provider: String? = null,
        @Query("subs_id") subsId: String? = null,
    ): Response<Unit>
}

/** Writes expose HTTP status; the spec leaves successful POST and GET bodies unspecified. */
interface MoviesBlacklistApi {
    @GET("api/movies/blacklist")
    suspend fun getMoviesBlacklist(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
    ): JsonObject

    @POST("api/movies/blacklist")
    suspend fun postMoviesBlacklist(
        @Query("radarrid") radarrId: Int,
        @Query("provider") provider: String,
        @Query("subs_id") subsId: String,
        @Query("language") language: String,
        @Query("subtitles_path") subtitlesPath: String,
    ): Response<JsonObject>

    @DELETE("api/movies/blacklist")
    suspend fun deleteMoviesBlacklist(
        @Query("all") all: String? = null,
        @Query("provider") provider: String? = null,
        @Query("subs_id") subsId: String? = null,
    ): Response<Unit>
}
