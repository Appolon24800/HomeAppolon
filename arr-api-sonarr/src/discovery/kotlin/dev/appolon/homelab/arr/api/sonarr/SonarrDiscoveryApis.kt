package dev.appolon.homelab.arr.api.sonarr

import retrofit2.Retrofit

/** Import lists, exclusions, custom filters, wanted missing and cutoff API factories. */
object SonarrDiscoveryApis {
    fun importList(retrofit: Retrofit): ImportListApi =
        retrofit.create(ImportListApi::class.java)
    fun importListConfig(retrofit: Retrofit): ImportListConfigApi =
        retrofit.create(ImportListConfigApi::class.java)
    fun importListExclusion(retrofit: Retrofit): ImportListExclusionApi =
        retrofit.create(ImportListExclusionApi::class.java)
    fun customFilter(retrofit: Retrofit): CustomFilterApi =
        retrofit.create(CustomFilterApi::class.java)
    fun missing(retrofit: Retrofit): MissingApi = retrofit.create(MissingApi::class.java)
    fun cutoff(retrofit: Retrofit): CutoffApi = retrofit.create(CutoffApi::class.java)
}
