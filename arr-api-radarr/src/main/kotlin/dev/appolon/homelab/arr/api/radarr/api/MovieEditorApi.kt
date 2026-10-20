package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.PUT

/** Radarr `MovieEditor` tag: bulk edit and bulk delete over many movies at once. */
interface MovieEditorApi {

    /** Applies the non-null fields of [editor] to every listed movie; 400 on invalid payloads. */
    @PUT("api/v3/movie/editor")
    suspend fun update(@Body editor: MovieEditorResource): Response<List<MovieResource>>

    /**
     * Bulk-deletes the listed movies per the editor flags. The ids travel in
     * the request body, so this needs Retrofit's raw `@HTTP` form: plain
     * `@DELETE` rejects any method carrying `@Body`.
     */
    @HTTP(method = "DELETE", path = "api/v3/movie/editor", hasBody = true)
    suspend fun delete(@Body editor: MovieEditorResource)
}
