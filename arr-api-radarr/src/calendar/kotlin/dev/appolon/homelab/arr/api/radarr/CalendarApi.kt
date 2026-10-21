package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged Calendar. `start` and `end` are ISO-8601 strings
 * passed through untouched; the UI formats and parses them with `:arr-core`
 * ArrTime.
 */
@JvmSuppressWildcards
interface CalendarApi {
    @GET("api/v3/calendar")
    suspend fun calendar(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("unmonitored") unmonitored: Boolean? = null,
        @Query("tags") tags: String? = null,
    ): List<MovieResource>
}
