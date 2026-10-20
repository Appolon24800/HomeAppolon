package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr Tag OpenAPI tag. */
@JvmSuppressWildcards
interface TagApi {
    @GET("api/v3/tag")
    suspend fun list(): List<TagResource>

    @POST("api/v3/tag")
    suspend fun create(@Body body: TagResource): TagResource

    @PUT("api/v3/tag/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: TagResource
    ): TagResource

    @DELETE("api/v3/tag/{id}")
    suspend fun delete(@Path("id") id: Int): Unit

    @GET("api/v3/tag/{id}")
    suspend fun get(@Path("id") id: Int): Response<TagResource>
}
