package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.UpdateResource
import retrofit2.http.GET

/** Radarr endpoints tagged Update. */
@JvmSuppressWildcards
interface UpdateApi {
    @GET("api/v3/update")
    suspend fun updates(): List<UpdateResource>
}
