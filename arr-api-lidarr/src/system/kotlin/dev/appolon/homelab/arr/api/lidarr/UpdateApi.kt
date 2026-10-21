package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.UpdateResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Lidarr endpoints tagged Update. */
@JvmSuppressWildcards
interface UpdateApi {
    @GET("api/v1/update")
    suspend fun getUpdates(): List<UpdateResource>
}
