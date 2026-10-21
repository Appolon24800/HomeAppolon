package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path

@JvmSuppressWildcards
interface LanguageApi {
    @GET("api/v1/language/{id}")
    suspend fun getLanguageById(@Path("id") id: Int): LanguageResource

    @GET("api/v1/language")
    suspend fun getLanguages(): List<LanguageResource>
}
