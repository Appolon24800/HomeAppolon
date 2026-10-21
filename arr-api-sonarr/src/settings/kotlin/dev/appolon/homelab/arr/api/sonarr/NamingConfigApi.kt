package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.NamingConfigResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Operations in the Sonarr NamingConfig OpenAPI tag. The spec declares no
 * response schema for the examples endpoint, so sample file names pass
 * through as raw JSON.
 */
@JvmSuppressWildcards
interface NamingConfigApi {
    @GET("api/v3/config/naming")
    suspend fun get(): NamingConfigResource

    @PUT("api/v3/config/naming/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: NamingConfigResource
    ): NamingConfigResource

    @GET("api/v3/config/naming/{id}")
    suspend fun get(@Path("id") id: Int): Response<NamingConfigResource>

    @GET("api/v3/config/naming/examples")
    suspend fun examples(
        @Query("renameEpisodes") renameEpisodes: Boolean? = null,
        @Query("replaceIllegalCharacters") replaceIllegalCharacters: Boolean? = null,
        @Query("colonReplacementFormat") colonReplacementFormat: Int? = null,
        @Query("customColonReplacementFormat") customColonReplacementFormat: String? = null,
        @Query("multiEpisodeStyle") multiEpisodeStyle: Int? = null,
        @Query("standardEpisodeFormat") standardEpisodeFormat: String? = null,
        @Query("dailyEpisodeFormat") dailyEpisodeFormat: String? = null,
        @Query("animeEpisodeFormat") animeEpisodeFormat: String? = null,
        @Query("seriesFolderFormat") seriesFolderFormat: String? = null,
        @Query("seasonFolderFormat") seasonFolderFormat: String? = null,
        @Query("specialsFolderFormat") specialsFolderFormat: String? = null,
        @Query("id") id: Int? = null,
        @Query("resourceName") resourceName: String? = null
    ): JsonObject
}
