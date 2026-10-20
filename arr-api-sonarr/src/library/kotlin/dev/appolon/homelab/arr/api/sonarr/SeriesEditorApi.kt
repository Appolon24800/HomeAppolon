package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.PUT

interface SeriesEditorApi {
    @PUT("api/v3/series/editor")
    suspend fun update(
        @Body body: SeriesEditorResource
    ): Unit

    @HTTP(method = "DELETE", path = "api/v3/series/editor", hasBody = true)
    suspend fun delete(
        @Body body: SeriesEditorResource
    ): Unit
}
