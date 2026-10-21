package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.TagDetailsResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * TagDetails tag: read-only views of which indexers, proxies and applications
 * use each tag, for delete confirmations.
 */
@JvmSuppressWildcards
interface TagDetailsApi {
    @GET("api/v1/tag/detail")
    suspend fun getTagDetails(): List<TagDetailsResource>

    @GET("api/v1/tag/detail/{id}")
    suspend fun getTagDetail(@Path("id") id: Int): TagDetailsResource
}
