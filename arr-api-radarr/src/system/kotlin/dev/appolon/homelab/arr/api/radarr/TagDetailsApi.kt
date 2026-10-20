package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Radarr endpoints tagged TagDetails. */
@JvmSuppressWildcards
interface TagDetailsApi {
    @GET("api/v3/tag/detail")
    suspend fun tagDetails(): List<TagDetailsResource>

    @GET("api/v3/tag/detail/{id}")
    suspend fun tagDetail(
        @Path("id") id: Int,
    ): Response<TagDetailsResource>
}
