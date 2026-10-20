package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MovieFolderResource
import retrofit2.http.GET
import retrofit2.http.Path

/** Radarr `MovieFolder` tag: the folder a movie is (or would be) stored in. */
interface MovieFolderApi {

    @GET("api/v3/movie/{id}/folder")
    suspend fun get(@Path("id") id: Int): MovieFolderResource
}
