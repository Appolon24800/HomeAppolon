package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface JellyfinApi {
    @GET("api/jellyfin/libraries")
    suspend fun getJellyfinLibraries(
        @Query("url") url: String? = null,
        @Query("apikey") apikey: String? = null,
    ): JsonObject

    @POST("api/jellyfin/test-connection")
    suspend fun postJellyfinTestConnection(
        @Query("url") url: String,
        @Query("apikey") apikey: String,
    ): JsonObject
}
