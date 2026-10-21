package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LocalizationLanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.LocalizationResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Operations in the Sonarr Localization OpenAPI tag. `strings` is a free-form
 * key/value object, so it passes through as raw JSON.
 */
@JvmSuppressWildcards
interface LocalizationApi {
    @GET("api/v3/localization")
    suspend fun get(): LocalizationResource

    @GET("api/v3/localization/language")
    suspend fun language(): LocalizationLanguageResource

    @GET("api/v3/localization/{id}")
    suspend fun get(@Path("id") id: Int): Response<LocalizationResource>
}
