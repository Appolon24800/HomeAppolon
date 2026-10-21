package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface FilesBrowserforBazarrApi {
    @GET("api/files")
    suspend fun getBrowseBazarrFs(@Query("path") path: String? = null): JsonObject
}
