package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.TagDetailsResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Operations in the Sonarr TagDetails OpenAPI tag. */
@JvmSuppressWildcards
interface TagDetailsApi {
    @GET("api/v3/tag/detail")
    suspend fun list(): List<TagDetailsResource>

    @GET("api/v3/tag/detail/{id}")
    suspend fun get(@Path("id") id: Int): Response<TagDetailsResource>
}
