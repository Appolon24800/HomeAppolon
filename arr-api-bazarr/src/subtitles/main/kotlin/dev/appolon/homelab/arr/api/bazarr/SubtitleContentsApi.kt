package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/** The spec does not describe the contents response, so it remains JSON passthrough. */
interface SubtitleContentsApi {
    @GET("api/subtitles/contents")
    suspend fun getSubtitleNameContents(
        @Query("subtitlePath") subtitlePath: String,
    ): JsonObject
}
