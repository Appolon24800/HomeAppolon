package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.NamingConfigResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged NamingConfig. The update path id is a string in the
 * spec. The examples operation takes the config fields as queries and has no
 * documented response schema, so the rendered examples pass through as raw JSON.
 */
@JvmSuppressWildcards
interface NamingConfigApi {
    @GET("api/v1/config/naming")
    suspend fun getNamingConfig(): NamingConfigResource

    @GET("api/v1/config/naming/{id}")
    suspend fun getNamingConfigById(@Path("id") id: Int): NamingConfigResource

    @PUT("api/v1/config/naming/{id}")
    suspend fun updateNamingConfig(
        @Path("id") id: String,
        @Body resource: NamingConfigResource,
    ): NamingConfigResource

    @GET("api/v1/config/naming/examples")
    suspend fun getNamingExamples(
        @Query("renameTracks") renameTracks: Boolean? = null,
        @Query("replaceIllegalCharacters") replaceIllegalCharacters: Boolean? = null,
        @Query("colonReplacementFormat") colonReplacementFormat: Int? = null,
        @Query("standardTrackFormat") standardTrackFormat: String? = null,
        @Query("multiDiscTrackFormat") multiDiscTrackFormat: String? = null,
        @Query("artistFolderFormat") artistFolderFormat: String? = null,
        @Query("includeArtistName") includeArtistName: Boolean? = null,
        @Query("includeAlbumTitle") includeAlbumTitle: Boolean? = null,
        @Query("includeQuality") includeQuality: Boolean? = null,
        @Query("replaceSpaces") replaceSpaces: Boolean? = null,
        @Query("separator") separator: String? = null,
        @Query("numberStyle") numberStyle: String? = null,
        @Query("id") id: Int? = null,
        @Query("resourceName") resourceName: String? = null,
    ): JsonObject
}
