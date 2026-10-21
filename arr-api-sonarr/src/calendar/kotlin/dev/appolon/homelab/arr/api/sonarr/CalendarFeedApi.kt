package dev.appolon.homelab.arr.api.sonarr

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Operations in the Sonarr CalendarFeed OpenAPI tag. The spec declares no
 * response schema for the iCalendar feed, so the body passes through raw and
 * callers parse the ICS text themselves. Status matters for feed consumers,
 * hence the [Response] wrapper.
 */
@JvmSuppressWildcards
interface CalendarFeedApi {
    @GET("feed/v3/calendar/sonarr.ics")
    suspend fun list(
        @Query("pastDays") pastDays: Int? = null,
        @Query("futureDays") futureDays: Int? = null,
        @Query("tags") tags: String? = null,
        @Query("unmonitored") unmonitored: Boolean? = null,
        @Query("premieresOnly") premieresOnly: Boolean? = null,
        @Query("asAllDay") asAllDay: Boolean? = null
    ): Response<ResponseBody>
}
