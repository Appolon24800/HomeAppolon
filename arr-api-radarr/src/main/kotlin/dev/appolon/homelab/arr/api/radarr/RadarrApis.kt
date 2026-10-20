package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.api.AlternativeTitleApi
import dev.appolon.homelab.arr.api.radarr.api.CreditApi
import dev.appolon.homelab.arr.api.radarr.api.ExtraFileApi
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieEditorApi
import dev.appolon.homelab.arr.api.radarr.api.MovieFileApi
import dev.appolon.homelab.arr.api.radarr.api.MovieFolderApi
import dev.appolon.homelab.arr.api.radarr.api.MovieImportApi
import dev.appolon.homelab.arr.api.radarr.api.MovieLookupApi
import retrofit2.Retrofit
import dev.appolon.homelab.arr.api.radarr.api.ReleaseApi

/**
 * Factory for every Retrofit interface this module owns. The caller supplies
 * the Retrofit instance so `:arr-data` keeps control of base URL, API key
 * header, proxy, and trust, per the architecture doc.
 */
object RadarrApis {
    fun release(retrofit: Retrofit): ReleaseApi = retrofit.create(ReleaseApi::class.java)

    fun movie(retrofit: Retrofit): MovieApi = retrofit.create(MovieApi::class.java)

    fun movieEditor(retrofit: Retrofit): MovieEditorApi =
        retrofit.create(MovieEditorApi::class.java)

    fun movieFile(retrofit: Retrofit): MovieFileApi = retrofit.create(MovieFileApi::class.java)

    fun movieFolder(retrofit: Retrofit): MovieFolderApi =
        retrofit.create(MovieFolderApi::class.java)

    fun movieImport(retrofit: Retrofit): MovieImportApi =
        retrofit.create(MovieImportApi::class.java)

    fun movieLookup(retrofit: Retrofit): MovieLookupApi =
        retrofit.create(MovieLookupApi::class.java)

    fun alternativeTitle(retrofit: Retrofit): AlternativeTitleApi =
        retrofit.create(AlternativeTitleApi::class.java)

    fun credit(retrofit: Retrofit): CreditApi = retrofit.create(CreditApi::class.java)

    fun extraFile(retrofit: Retrofit): ExtraFileApi = retrofit.create(ExtraFileApi::class.java)
}
