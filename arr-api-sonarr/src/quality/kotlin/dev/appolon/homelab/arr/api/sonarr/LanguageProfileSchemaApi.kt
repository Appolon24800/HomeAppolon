package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import retrofit2.http.GET

/**
 * Operations in the Sonarr LanguageProfileSchema OpenAPI tag. The schema
 * endpoint returns a default LanguageProfileResource template.
 */
@JvmSuppressWildcards
interface LanguageProfileSchemaApi {
    @GET("api/v3/languageprofile/schema")
    suspend fun list(): LanguageProfileResource
}
