package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface SubtitlesInfoApi {
    @GET("api/subtitles/info")
    suspend fun getSubtitleNameInfo(
        @Query("filenames[]") filenames: List<String>,
    ): JsonObject
}
