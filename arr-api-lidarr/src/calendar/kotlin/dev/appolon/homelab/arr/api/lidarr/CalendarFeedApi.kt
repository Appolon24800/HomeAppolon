package dev.appolon.homelab.arr.api.lidarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * iCal subscription feed. The spec declares no response content for the .ics
 * endpoint, so the raw calendar text passes through for the caller to parse.
 */
@JvmSuppressWildcards
interface CalendarFeedApi {
    @GET("feed/v1/calendar/lidarr.ics")
    suspend fun getCalendarFeed(
        @Query("pastDays") pastDays: Int? = 7,
        @Query("futureDays") futureDays: Int? = 28,
        @Query("tags") tags: String? = "",
        @Query("unmonitored") unmonitored: Boolean? = false,
    ): ResponseBody
}
