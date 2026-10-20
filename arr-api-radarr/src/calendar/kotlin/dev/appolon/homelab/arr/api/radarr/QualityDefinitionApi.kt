package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged QualityDefinition. The spec types the PUT path id as
 * a string and the GET path id as an integer; both address the same row.
 */
@JvmSuppressWildcards
interface QualityDefinitionApi {
    @GET("api/v3/qualitydefinition")
    suspend fun qualityDefinitions(): List<QualityDefinitionResource>

    /** The spec documents a 404 for an unknown definition id. */
    @GET("api/v3/qualitydefinition/{id}")
    suspend fun qualityDefinition(
        @Path("id") id: Int,
    ): Response<QualityDefinitionResource>

    @PUT("api/v3/qualitydefinition/{id}")
    suspend fun updateQualityDefinition(
        @Path("id") id: String,
        @Body body: QualityDefinitionResource,
    ): Response<QualityDefinitionResource>

    @PUT("api/v3/qualitydefinition/update")
    suspend fun updateQualityDefinitions(
        @Body body: List<QualityDefinitionResource>,
    ): List<QualityDefinitionResource>

    @GET("api/v3/qualitydefinition/limits")
    suspend fun qualityDefinitionLimits(): QualityDefinitionLimitsResource
}
