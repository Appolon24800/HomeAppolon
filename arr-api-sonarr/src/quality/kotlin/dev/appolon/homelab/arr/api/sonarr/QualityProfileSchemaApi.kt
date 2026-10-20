package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import retrofit2.http.GET

/**
 * Operations in the Sonarr QualityProfileSchema OpenAPI tag. The schema
 * endpoint returns a default QualityProfileResource template.
 */
@JvmSuppressWildcards
interface QualityProfileSchemaApi {
    @GET("api/v3/qualityprofile/schema")
    suspend fun list(): QualityProfileResource
}
