package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** ArtistLookup endpoints from the checked-in Lidarr specification. */
interface ArtistLookupApi {
    @GET("api/v1/artist/lookup")
    suspend fun lookupArtists(
        @Query("term") term: String? = null,
    ): List<ArtistResource>

}
