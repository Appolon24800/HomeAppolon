package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface FilesBrowserforRadarrApi {
    @GET("api/files/radarr")
    suspend fun getBrowseRadarrFs(@Query("path") path: String? = null): JsonObject
}
