package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged Tag. The update path id is a string in the spec,
 * unlike the integer ids on get and delete.
 */
@JvmSuppressWildcards
interface TagApi {
    @GET("api/v1/tag")
    suspend fun getTags(): List<TagResource>

    @GET("api/v1/tag/{id}")
    suspend fun getTag(@Path("id") id: Int): TagResource

    @POST("api/v1/tag")
    suspend fun addTag(@Body resource: TagResource): TagResource

    @PUT("api/v1/tag/{id}")
    suspend fun updateTag(
        @Path("id") id: String,
        @Body resource: TagResource,
    ): TagResource

    @DELETE("api/v1/tag/{id}")
    suspend fun deleteTag(@Path("id") id: Int)
}
