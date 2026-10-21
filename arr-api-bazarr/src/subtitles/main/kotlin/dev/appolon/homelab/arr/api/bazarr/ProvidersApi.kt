package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ProvidersApi {
    @POST("api/providers")
    suspend fun postProviders(@Query("action") action: String): Response<Unit>

    @GET("api/providers")
    suspend fun getProviders(@Query("history") history: String? = null): JsonObject
}
