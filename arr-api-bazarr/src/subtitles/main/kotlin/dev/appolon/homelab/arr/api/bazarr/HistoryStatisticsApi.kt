package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface HistoryStatisticsApi {
    @GET("api/history/stats")
    suspend fun getHistoryStats(
        @Query("timeFrame") timeFrame: String? = null,
        @Query("action") action: String? = null,
        @Query("provider") provider: String? = null,
        @Query("language") language: String? = null,
    ): JsonObject
}
