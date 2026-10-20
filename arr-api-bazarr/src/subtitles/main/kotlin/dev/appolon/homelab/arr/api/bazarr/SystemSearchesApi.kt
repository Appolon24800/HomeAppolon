package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface SystemSearchesApi {
    @GET("api/system/searches")
    suspend fun getSearches(@Query("query") query: String): JsonObject
}
