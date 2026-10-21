package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import retrofit2.http.GET
import retrofit2.http.Query

/** ArtistLookup endpoints from the checked-in Lidarr specification. */
interface ArtistLookupApi {
    @GET("api/v1/artist/lookup")
    suspend fun lookupArtists(
        @Query("term") term: String? = null,
    ): List<ArtistResource>

}
