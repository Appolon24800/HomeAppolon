package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Radarr endpoints tagged ManualImport. */
@JvmSuppressWildcards

interface ManualImportApi {
    @GET("api/v3/manualimport")
    suspend fun manualImport(
        @Query("folder") folder: String? = null,
        @Query("downloadId") downloadId: String? = null,
        @Query("movieId") movieId: Int? = null,
        @Query("filterExistingFiles") filterExistingFiles: Boolean? = null,
    ): List<ManualImportResource>

    @POST("api/v3/manualimport")
    suspend fun reprocessManualImport(
        @Body body: List<ManualImportReprocessResource>,
    ): Response<List<ManualImportReprocessResource>>

}
