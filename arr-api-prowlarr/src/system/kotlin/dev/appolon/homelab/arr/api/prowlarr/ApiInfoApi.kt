package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ApiInfoResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** ApiInfo tag: which API versions the server exposes. */
@JvmSuppressWildcards
interface ApiInfoApi {
    @GET("api")
    suspend fun getApiInfo(): ApiInfoResource
}
