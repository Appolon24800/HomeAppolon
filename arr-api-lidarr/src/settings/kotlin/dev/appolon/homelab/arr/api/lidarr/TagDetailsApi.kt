package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TagDetailsResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path

/** Lidarr endpoints tagged TagDetails, the tag to consumer index. */
@JvmSuppressWildcards
interface TagDetailsApi {
    @GET("api/v1/tag/detail")
    suspend fun getTagDetails(): List<TagDetailsResource>

    @GET("api/v1/tag/detail/{id}")
    suspend fun getTagDetailsById(@Path("id") id: Int): TagDetailsResource
}
