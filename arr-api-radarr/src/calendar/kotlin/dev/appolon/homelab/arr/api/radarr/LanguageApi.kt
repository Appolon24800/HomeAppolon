package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.jvm.JvmSuppressWildcards

/** Radarr endpoints tagged Language, the read-only language lookup table. */
@JvmSuppressWildcards
interface LanguageApi {
    @GET("api/v3/language")
    suspend fun languages(): List<LanguageResource>

    /** The spec documents a 404 for an unknown language id. */
    @GET("api/v3/language/{id}")
    suspend fun language(
        @Path("id") id: Int,
    ): Response<LanguageResource>
}
