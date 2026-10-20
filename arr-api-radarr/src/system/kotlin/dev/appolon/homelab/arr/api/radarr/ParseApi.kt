package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.ParseResource
import retrofit2.http.GET
import retrofit2.http.Query

/** Radarr endpoints tagged Parse. */
@JvmSuppressWildcards
interface ParseApi {
    @GET("api/v3/parse")
    suspend fun parse(
        @Query("title") title: String? = null,
    ): ParseResource
}
