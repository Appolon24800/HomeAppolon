package dev.appolon.homelab.arr.api.sonarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

interface SeriesFolderApi {
    /** The specification omits the response schema; preserve the JSON unchanged. */
    @GET("api/v3/series/{id}/folder")
    suspend fun get(
        @Path("id") id: Int
    ): JsonObject
}
