package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ProvidersEpisodesApi {
    @POST("api/providers/episodes")
    suspend fun postProviderEpisodes(
        @Query("seriesid") seriesid: Int,
        @Query("episodeid") episodeid: Int,
        @Query("hi") hi: String,
        @Query("forced") forced: String,
        @Query("original_format") originalFormat: String,
        @Query("provider") provider: String,
        @Query("subtitle") subtitle: String,
    ): Response<Unit>

    @GET("api/providers/episodes")
    suspend fun getProviderEpisodes(@Query("episodeid") episodeid: Int): JsonObject
}
