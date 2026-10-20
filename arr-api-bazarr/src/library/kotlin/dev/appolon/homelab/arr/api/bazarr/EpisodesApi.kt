package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/** The spec requires either series IDs or episode IDs but leaves the body undocumented. */
interface EpisodesApi {
    @GET("episodes")
    suspend fun getEpisodes(
        @Query("seriesid[]") seriesIds: List<Int>? = null,
        @Query("episodeid[]") episodeIds: List<Int>? = null,
    ): JsonObject
}
