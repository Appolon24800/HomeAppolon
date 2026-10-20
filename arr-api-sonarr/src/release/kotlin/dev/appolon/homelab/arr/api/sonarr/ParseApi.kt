package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ParseResource
import retrofit2.http.GET
import retrofit2.http.Query

/** Operations in the Sonarr Parse OpenAPI tag. */
@JvmSuppressWildcards
interface ParseApi {
    @GET("api/v3/parse")
    suspend fun list(
        @Query("title") title: String? = null,
        @Query("path") path: String? = null
    ): ParseResource
}
