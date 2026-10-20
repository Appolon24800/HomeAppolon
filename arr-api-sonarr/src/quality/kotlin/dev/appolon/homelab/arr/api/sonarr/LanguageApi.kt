package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Operations in the Sonarr Language OpenAPI tag. */
@JvmSuppressWildcards
interface LanguageApi {
    @GET("api/v3/language")
    suspend fun list(): List<LanguageResource>

    @GET("api/v3/language/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<LanguageResource>
}
