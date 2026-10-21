package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.UpdateResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Update tag: published server updates for the update screen. */
@JvmSuppressWildcards
interface UpdateApi {
    @GET("api/v1/update")
    suspend fun getUpdates(): List<UpdateResource>
}
