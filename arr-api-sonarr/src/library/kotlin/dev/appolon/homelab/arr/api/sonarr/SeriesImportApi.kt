package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import retrofit2.http.Body
import retrofit2.http.POST

interface SeriesImportApi {
    @POST("api/v3/series/import")
    suspend fun create(
        @Body body: List<SeriesResource>
    ): Unit
}
