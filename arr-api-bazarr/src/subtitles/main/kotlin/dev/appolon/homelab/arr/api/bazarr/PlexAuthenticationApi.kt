package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.api.bazarr.model.PlexSelectServerRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlexAuthenticationApi {
    @POST("api/plex/apikey")
    suspend fun postPlexApiKey(@Query("apikey") apikey: String): JsonObject

    @GET("api/plex/autopulse/config")
    suspend fun getPlexAutopulseConfig(): JsonObject

    @POST("api/plex/encrypt-apikey")
    suspend fun postPlexEncryptApiKey(): JsonObject

    @GET("api/plex/oauth/libraries")
    suspend fun getPlexLibraries(): JsonObject

    @POST("api/plex/oauth/logout")
    suspend fun postPlexLogout(): JsonObject

    @POST("api/plex/oauth/pin")
    suspend fun postPlexPin(@Query("clientId") clientId: String? = null): JsonObject

    @GET("api/plex/oauth/pin")
    suspend fun getPlexPin(): JsonObject

    /** The spec omits the parameter declaration but the path requires pin_id. */
    @GET("api/plex/oauth/pin/{pin_id}/check")
    suspend fun getPlexPinCheck(@Path("pin_id") pinId: String): JsonObject

    @GET("api/plex/oauth/servers")
    suspend fun getPlexServers(): JsonObject

    @GET("api/plex/oauth/validate")
    suspend fun getPlexValidate(): JsonObject

    @POST("api/plex/select-server")
    suspend fun postPlexSelectServer(
        @Query("machineIdentifier") machineIdentifier: String,
        @Query("name") name: String,
        @Query("uri") uri: String,
        @Body payload: PlexSelectServerRequest,
        @Query("local") local: String? = null,
    ): JsonObject

    @GET("api/plex/select-server")
    suspend fun getPlexSelectServer(): JsonObject

    @POST("api/plex/test-connection")
    suspend fun postPlexTestConnection(@Query("uri") uri: String): JsonObject

    @GET("api/plex/test-connection")
    suspend fun getPlexTestConnection(): JsonObject

    @POST("api/plex/webhook/create")
    suspend fun postPlexWebhookCreate(): JsonObject

    @POST("api/plex/webhook/delete")
    suspend fun postPlexWebhookDelete(@Query("webhook_url") webhookUrl: String): JsonObject

    @GET("api/plex/webhook/list")
    suspend fun getPlexWebhookList(): JsonObject
}
