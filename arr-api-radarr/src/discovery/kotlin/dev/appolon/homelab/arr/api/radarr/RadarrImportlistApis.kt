package dev.appolon.homelab.arr.api.radarr

import retrofit2.Retrofit

/** Builds the import list, discovery, collection, and wanted clients on a connection. */
object RadarrImportlistApis {
    fun importList(retrofit: Retrofit): ImportListApi =
        retrofit.create(ImportListApi::class.java)

    fun importListConfig(retrofit: Retrofit): ImportListConfigApi =
        retrofit.create(ImportListConfigApi::class.java)

    fun importListExclusion(retrofit: Retrofit): ImportListExclusionApi =
        retrofit.create(ImportListExclusionApi::class.java)

    fun importListMovies(retrofit: Retrofit): ImportListMoviesApi =
        retrofit.create(ImportListMoviesApi::class.java)

    fun collection(retrofit: Retrofit): CollectionApi =
        retrofit.create(CollectionApi::class.java)

    fun customFilter(retrofit: Retrofit): CustomFilterApi =
        retrofit.create(CustomFilterApi::class.java)

    fun missing(retrofit: Retrofit): MissingApi =
        retrofit.create(MissingApi::class.java)

    fun cutoff(retrofit: Retrofit): CutoffApi =
        retrofit.create(CutoffApi::class.java)

}
