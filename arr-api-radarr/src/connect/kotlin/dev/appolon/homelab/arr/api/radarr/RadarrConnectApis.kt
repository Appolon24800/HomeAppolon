package dev.appolon.homelab.arr.api.radarr

import retrofit2.Retrofit

/** Builds the connect-typed clients (download clients, indexers, notifications,
 * remote path mappings, and their settings) on a supplied connection. */
object RadarrConnectApis {
    fun downloadClient(retrofit: Retrofit): DownloadClientApi =
        retrofit.create(DownloadClientApi::class.java)

    fun downloadClientConfig(retrofit: Retrofit): DownloadClientConfigApi =
        retrofit.create(DownloadClientConfigApi::class.java)

    fun indexer(retrofit: Retrofit): IndexerApi =
        retrofit.create(IndexerApi::class.java)

    fun indexerConfig(retrofit: Retrofit): IndexerConfigApi =
        retrofit.create(IndexerConfigApi::class.java)

    fun indexerFlag(retrofit: Retrofit): IndexerFlagApi =
        retrofit.create(IndexerFlagApi::class.java)

    fun notification(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)

    fun remotePathMapping(retrofit: Retrofit): RemotePathMappingApi =
        retrofit.create(RemotePathMappingApi::class.java)

}
