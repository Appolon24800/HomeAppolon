package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Tag tag: CRUD on free-form labels. PUT path ids are strings because the
 * spec declares that type on update routes.
 */
@JvmSuppressWildcards
interface TagApi {
    @GET("api/v1/tag")
    suspend fun getTags(): List<TagResource>

    @GET("api/v1/tag/{id}")
    suspend fun getTag(@Path("id") id: Int): TagResource

    @POST("api/v1/tag")
    suspend fun createTag(@Body resource: TagResource): TagResource

    @PUT("api/v1/tag/{id}")
    suspend fun updateTag(
        @Path("id") id: String,
        @Body resource: TagResource,
    ): TagResource

    @DELETE("api/v1/tag/{id}")
    suspend fun deleteTag(@Path("id") id: Int): Response<Unit>
}
