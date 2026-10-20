package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import retrofit2.http.GET
import retrofit2.http.Query

/** AlbumLookup endpoints from the checked-in Lidarr specification. */
interface AlbumLookupApi {
    @GET("api/v1/album/lookup")
    suspend fun lookupAlbums(
        @Query("term") term: String? = null,
    ): List<AlbumResource>

}
