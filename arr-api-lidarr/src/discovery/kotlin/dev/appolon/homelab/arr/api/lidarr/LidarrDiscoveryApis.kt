package dev.appolon.homelab.arr.api.lidarr

import retrofit2.Retrofit

/** Import list, wanted, custom filter, search and static clients for one server. */
object LidarrDiscoveryApis {
    fun importList(retrofit: Retrofit): ImportListApi = retrofit.create(ImportListApi::class.java)
    fun importListExclusion(retrofit: Retrofit): ImportListExclusionApi =
        retrofit.create(ImportListExclusionApi::class.java)
    fun customFilter(retrofit: Retrofit): CustomFilterApi =
        retrofit.create(CustomFilterApi::class.java)
    fun missing(retrofit: Retrofit): MissingApi = retrofit.create(MissingApi::class.java)
    fun cutoff(retrofit: Retrofit): CutoffApi = retrofit.create(CutoffApi::class.java)
    fun search(retrofit: Retrofit): SearchApi = retrofit.create(SearchApi::class.java)
    fun staticResource(retrofit: Retrofit): StaticResourceApi =
        retrofit.create(StaticResourceApi::class.java)
}
