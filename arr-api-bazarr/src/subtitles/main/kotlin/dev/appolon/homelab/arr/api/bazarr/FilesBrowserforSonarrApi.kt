package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface FilesBrowserforSonarrApi {
    @GET("api/files/sonarr")
    suspend fun getBrowseSonarrFs(@Query("path") path: String? = null): JsonObject
}
