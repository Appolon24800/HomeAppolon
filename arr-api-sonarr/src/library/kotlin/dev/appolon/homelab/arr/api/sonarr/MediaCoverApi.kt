package dev.appolon.homelab.arr.api.sonarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

interface MediaCoverApi {
    /** The specification omits the response schema; preserve the JSON unchanged. */
    @GET("api/v3/mediacover/{seriesId}/{filename}")
    suspend fun get(
        @Path("seriesId") seriesId: Int,
        @Path("filename") filename: String
    ): JsonObject
}
