package dev.appolon.homelab.arr.api.prowlarr

import retrofit2.Retrofit

/**
 * Creates every indexer-group interface from a connection-layer Retrofit
 * instance that already carries the base URL with path prefix, X-Api-Key and
 * the shared ArrJson converter.
 */
object ProwlarrIndexersApis {
    fun indexer(retrofit: Retrofit): IndexerApi = retrofit.create(IndexerApi::class.java)
    fun indexerDefaultCategories(retrofit: Retrofit): IndexerDefaultCategoriesApi =
        retrofit.create(IndexerDefaultCategoriesApi::class.java)
    fun indexerStatus(retrofit: Retrofit): IndexerStatusApi =
        retrofit.create(IndexerStatusApi::class.java)
    fun indexerProxy(retrofit: Retrofit): IndexerProxyApi =
        retrofit.create(IndexerProxyApi::class.java)
    fun appProfile(retrofit: Retrofit): AppProfileApi =
        retrofit.create(AppProfileApi::class.java)
    fun application(retrofit: Retrofit): ApplicationApi =
        retrofit.create(ApplicationApi::class.java)
    fun tag(retrofit: Retrofit): TagApi = retrofit.create(TagApi::class.java)
    fun tagDetails(retrofit: Retrofit): TagDetailsApi =
        retrofit.create(TagDetailsApi::class.java)
}
