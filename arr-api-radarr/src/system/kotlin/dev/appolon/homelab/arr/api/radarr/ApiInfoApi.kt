package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.ApiInfoResource
import retrofit2.http.GET

/** Radarr endpoints tagged ApiInfo. */
@JvmSuppressWildcards
interface ApiInfoApi {
    @GET("api")
    suspend fun apiInfo(): ApiInfoResource
}
