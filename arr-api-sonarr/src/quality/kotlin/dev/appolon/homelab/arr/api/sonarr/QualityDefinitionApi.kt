package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import retrofit2.http.Body
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr QualityDefinition OpenAPI tag. */
@JvmSuppressWildcards
interface QualityDefinitionApi {
    @GET("api/v3/qualitydefinition")
    suspend fun list(): List<QualityDefinitionResource>

    @PUT("api/v3/qualitydefinition/update")
    suspend fun updateMany(
        @Body body: List<QualityDefinitionResource>
    ): Unit

    @GET("api/v3/qualitydefinition/limits")
    suspend fun limits(): QualityDefinitionLimitsResource

    @PUT("api/v3/qualitydefinition/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: QualityDefinitionResource
    ): QualityDefinitionResource

    @GET("api/v3/qualitydefinition/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<QualityDefinitionResource>
}
