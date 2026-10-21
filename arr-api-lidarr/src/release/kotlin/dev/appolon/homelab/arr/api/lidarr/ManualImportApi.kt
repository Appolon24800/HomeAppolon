package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ManualImportApi {
    @POST("api/v1/manualimport")
    suspend fun updateManualImport(@Body body: List<ManualImportUpdateResource>)

    @GET("api/v1/manualimport")
    suspend fun getManualImport(
        @Query("folder") folder: String? = null,
        @Query("downloadId") downloadId: String? = null,
        @Query("artistId") artistId: Int? = null,
        @Query("filterExistingFiles") filterExistingFiles: Boolean? = null,
        @Query("replaceExistingFiles") replaceExistingFiles: Boolean? = null,
    ): List<ManualImportResource>
}
