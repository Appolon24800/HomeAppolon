package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import retrofit2.http.Body
import retrofit2.http.POST

interface SeasonPassApi {
    @POST("api/v3/seasonpass")
    suspend fun create(
        @Body body: SeasonPassResource
    ): Unit
}
