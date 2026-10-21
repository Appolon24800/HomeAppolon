package dev.appolon.homelab.arr.api.prowlarr

import retrofit2.Retrofit

/**
 * Creates every search-group interface from a connection-layer Retrofit
 * instance that already carries the base URL with path prefix, X-Api-Key and
 * the shared ArrJson converter.
 */
object ProwlarrSearchApis {
    fun search(retrofit: Retrofit): SearchApi = retrofit.create(SearchApi::class.java)
    fun newznab(retrofit: Retrofit): NewznabApi = retrofit.create(NewznabApi::class.java)
    fun history(retrofit: Retrofit): HistoryApi = retrofit.create(HistoryApi::class.java)
    fun indexerStats(retrofit: Retrofit): IndexerStatsApi =
        retrofit.create(IndexerStatsApi::class.java)
    fun customFilter(retrofit: Retrofit): CustomFilterApi =
        retrofit.create(CustomFilterApi::class.java)
    fun command(retrofit: Retrofit): CommandApi = retrofit.create(CommandApi::class.java)
}
