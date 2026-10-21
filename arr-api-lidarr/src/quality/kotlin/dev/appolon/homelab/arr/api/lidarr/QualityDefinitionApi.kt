package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface QualityDefinitionApi {
    @PUT("api/v1/qualitydefinition/{id}")
    suspend fun updateQualityDefinition(
        @Path("id") id: String,
        @Body resource: QualityDefinitionResource,
    ): QualityDefinitionResource

    @GET("api/v1/qualitydefinition/{id}")
    suspend fun getQualityDefinitionById(@Path("id") id: Int): QualityDefinitionResource

    @GET("api/v1/qualitydefinition")
    suspend fun getQualityDefinitions(): List<QualityDefinitionResource>

    @PUT("api/v1/qualitydefinition/update")
    suspend fun updateQualityDefinitions(@Body resource: List<QualityDefinitionResource>)
}
