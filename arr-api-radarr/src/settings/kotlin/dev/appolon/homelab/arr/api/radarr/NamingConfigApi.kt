package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingExampleResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged NamingConfig. */
@JvmSuppressWildcards
interface NamingConfigApi {
    @GET("api/v3/config/naming")
    suspend fun namingConfig(): NamingConfigResource

    /** The spec declares this path id as a string. */
    @PUT("api/v3/config/naming/{id}")
    suspend fun updateNamingConfig(
        @Path("id") id: String,
        @Body body: NamingConfigResource,
    ): Response<NamingConfigResource>

    @GET("api/v3/config/naming/{id}")
    suspend fun namingConfigById(
        @Path("id") id: Int,
    ): Response<NamingConfigResource>

    /** Preview endpoint: every query is optional and echoes the desired tokens. */
    @GET("api/v3/config/naming/examples")
    suspend fun namingExamples(
        @Query("renameMovies") renameMovies: Boolean? = null,
        @Query("replaceIllegalCharacters") replaceIllegalCharacters: Boolean? = null,
        @Query("colonReplacementFormat") colonReplacementFormat: ColonReplacementFormat? = null,
        @Query("standardMovieFormat") standardMovieFormat: String? = null,
        @Query("movieFolderFormat") movieFolderFormat: String? = null,
        @Query("id") id: Int? = null,
        @Query("resourceName") resourceName: String? = null,
    ): NamingExampleResource
}
