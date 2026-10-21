package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.LocalizationLanguageResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/** Radarr endpoints tagged Localization. */
@JvmSuppressWildcards
interface LocalizationApi {
    /** The spec documents no response schema for this operation; JSON passes through as [JsonObject]. */
    @GET("api/v3/localization")
    suspend fun localization(): JsonObject

    @GET("api/v3/localization/language")
    suspend fun localizationLanguage(): LocalizationLanguageResource
}
