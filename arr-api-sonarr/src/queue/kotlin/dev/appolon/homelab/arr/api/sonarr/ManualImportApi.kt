package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Operations in the Sonarr ManualImport OpenAPI tag. */
@JvmSuppressWildcards
interface ManualImportApi {
    @GET("api/v3/manualimport")
    suspend fun list(
        @Query("folder") folder: String? = null,
        @Query("downloadId") downloadId: String? = null,
        @Query("seriesId") seriesId: Int? = null,
        @Query("seasonNumber") seasonNumber: Int? = null,
        @Query("filterExistingFiles") filterExistingFiles: Boolean? = null
    ): List<ManualImportResource>

    @POST("api/v3/manualimport")
    suspend fun create(
        @Body body: List<ManualImportReprocessResource>
    ): Unit
}
