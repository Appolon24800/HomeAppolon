package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CalendarReleaseType
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged CalendarFeed. The spec documents only a 204 response
 * and gives the iCalendar body no schema, so success is reported by status
 * alone and callers that need the .ics payload fetch the URL directly.
 */
@JvmSuppressWildcards
interface CalendarFeedApi {
    @GET("feed/v3/calendar/radarr.ics")
    suspend fun calendarFeed(
        @Query("pastDays") pastDays: Int? = null,
        @Query("futureDays") futureDays: Int? = null,
        @Query("tags") tags: String? = null,
        @Query("unmonitored") unmonitored: Boolean? = null,
        @Query("releaseTypes") releaseTypes: List<CalendarReleaseType>? = null,
    ): Response<Unit>
}
