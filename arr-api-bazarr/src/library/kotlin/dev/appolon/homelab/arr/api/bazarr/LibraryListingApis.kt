package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/** Tag responses have no schema in the bundled spec. */
interface SeriesTagsApi {
    @GET("api/series/tags")
    suspend fun getSeriesTags(): JsonObject
}

/** Tag responses have no schema in the bundled spec. */
interface MoviesTagsApi {
    @GET("api/movies/tags")
    suspend fun getMoviesTags(): JsonObject
}

/** Badge responses have no schema in the bundled spec. */
interface BadgesApi {
    @GET("api/badges")
    suspend fun getBadges(): JsonObject
}

/** History dates remain untouched inside the undocumented response object. */
interface EpisodesHistoryApi {
    @GET("api/episodes/history")
    suspend fun getEpisodesHistory(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
        @Query("episodeid") episodeId: Int? = null,
    ): JsonObject
}

/** History dates remain untouched inside the undocumented response object. */
interface MoviesHistoryApi {
    @GET("api/movies/history")
    suspend fun getMoviesHistory(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
        @Query("radarrid") radarrId: Int? = null,
    ): JsonObject
}

/** Wanted response bodies have no schema in the bundled spec. */
interface EpisodesWantedApi {
    @GET("api/episodes/wanted")
    suspend fun getEpisodesWanted(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
        @Query("episodeid[]") episodeIds: List<Int>? = null,
    ): JsonObject
}

/** Wanted response bodies have no schema in the bundled spec. */
interface MoviesWantedApi {
    @GET("api/movies/wanted")
    suspend fun getMoviesWanted(
        @Query("start") start: Int? = null,
        @Query("length") length: Int? = null,
        @Query("radarrid[]") radarrIds: List<Int>? = null,
    ): JsonObject
}
