package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import retrofit2.http.Body
import retrofit2.http.POST

/** Radarr `MovieImport` tag: bulk import of already-parsed movies. */
interface MovieImportApi {

    /** Imports the posted movies (as produced by lookup plus an import list scan) in one call. */
    @POST("api/v3/movie/import")
    suspend fun importMovies(@Body movies: List<MovieResource>): List<MovieResource>
}
