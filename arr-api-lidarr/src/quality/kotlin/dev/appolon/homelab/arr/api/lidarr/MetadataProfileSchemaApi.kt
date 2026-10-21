package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Returns the metadata profile template the server prefills new profiles with. */
@JvmSuppressWildcards
interface MetadataProfileSchemaApi {
    @GET("api/v1/metadataprofile/schema")
    suspend fun getMetadataProfileSchema(): MetadataProfileResource
}
