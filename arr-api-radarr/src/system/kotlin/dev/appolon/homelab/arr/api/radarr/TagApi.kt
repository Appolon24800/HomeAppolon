package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.TagResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged Tag. */
@JvmSuppressWildcards
interface TagApi {
    @GET("api/v3/tag")
    suspend fun tags(): List<TagResource>

    @POST("api/v3/tag")
    suspend fun createTag(
        @Body body: TagResource,
    ): Response<TagResource>

    /** The spec declares this path id as a string. */
    @PUT("api/v3/tag/{id}")
    suspend fun updateTag(
        @Path("id") id: String,
        @Body body: TagResource,
    ): Response<TagResource>

    @DELETE("api/v3/tag/{id}")
    suspend fun removeTag(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/tag/{id}")
    suspend fun tag(
        @Path("id") id: Int,
    ): Response<TagResource>
}
