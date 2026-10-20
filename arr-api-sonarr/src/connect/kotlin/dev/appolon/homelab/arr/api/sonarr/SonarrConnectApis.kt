package dev.appolon.homelab.arr.api.sonarr

import retrofit2.Retrofit

/** Creates every connect-tag Sonarr API from a shared [Retrofit] instance. */
object SonarrConnectApis {
    fun downloadClient(retrofit: Retrofit): DownloadClientApi =
        retrofit.create(DownloadClientApi::class.java)
    fun downloadClientConfig(retrofit: Retrofit): DownloadClientConfigApi =
        retrofit.create(DownloadClientConfigApi::class.java)
    fun indexer(retrofit: Retrofit): IndexerApi = retrofit.create(IndexerApi::class.java)
    fun indexerConfig(retrofit: Retrofit): IndexerConfigApi =
        retrofit.create(IndexerConfigApi::class.java)
    fun indexerFlag(retrofit: Retrofit): IndexerFlagApi =
        retrofit.create(IndexerFlagApi::class.java)
    fun notification(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)
    fun remotePathMapping(retrofit: Retrofit): RemotePathMappingApi =
        retrofit.create(RemotePathMappingApi::class.java)
}
