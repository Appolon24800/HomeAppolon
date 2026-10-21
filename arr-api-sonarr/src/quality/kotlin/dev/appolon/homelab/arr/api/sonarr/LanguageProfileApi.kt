package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr LanguageProfile OpenAPI tag. */
@JvmSuppressWildcards
interface LanguageProfileApi {
    @POST("api/v3/languageprofile")
    suspend fun create(
        @Body body: LanguageProfileResource
    ): LanguageProfileResource

    @GET("api/v3/languageprofile")
    suspend fun list(): List<LanguageProfileResource>

    @DELETE("api/v3/languageprofile/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @PUT("api/v3/languageprofile/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: LanguageProfileResource
    ): LanguageProfileResource

    @GET("api/v3/languageprofile/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<LanguageProfileResource>
}
