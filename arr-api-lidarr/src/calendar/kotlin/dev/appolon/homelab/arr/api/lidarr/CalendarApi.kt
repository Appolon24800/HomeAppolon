package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Upcoming and recent albums; Lidarr models calendar entries as albums. */
@JvmSuppressWildcards
interface CalendarApi {
    @GET("api/v1/calendar")
    suspend fun getCalendar(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("unmonitored") unmonitored: Boolean? = false,
        @Query("includeArtist") includeArtist: Boolean? = false,
        @Query("tags") tags: String? = "",
    ): List<AlbumResource>

    @GET("api/v1/calendar/{id}")
    suspend fun getCalendarById(@Path("id") id: Int): AlbumResource
}
