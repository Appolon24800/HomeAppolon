package dev.appolon.homelab.arr.api.prowlarr

import retrofit2.Retrofit

/**
 * Creates every settings-group interface from a connection-layer Retrofit
 * instance that already carries the base URL with path prefix, X-Api-Key and
 * the shared ArrJson converter.
 */
object ProwlarrSettingsApis {
    fun downloadClient(retrofit: Retrofit): DownloadClientApi =
        retrofit.create(DownloadClientApi::class.java)
    fun downloadClientConfig(retrofit: Retrofit): DownloadClientConfigApi =
        retrofit.create(DownloadClientConfigApi::class.java)
    fun notification(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)
    fun hostConfig(retrofit: Retrofit): HostConfigApi =
        retrofit.create(HostConfigApi::class.java)
    fun developmentConfig(retrofit: Retrofit): DevelopmentConfigApi =
        retrofit.create(DevelopmentConfigApi::class.java)
    fun uiConfig(retrofit: Retrofit): UiConfigApi =
        retrofit.create(UiConfigApi::class.java)
    fun authentication(retrofit: Retrofit): AuthenticationApi =
        retrofit.create(AuthenticationApi::class.java)
    fun fileSystem(retrofit: Retrofit): FileSystemApi =
        retrofit.create(FileSystemApi::class.java)
    fun localization(retrofit: Retrofit): LocalizationApi =
        retrofit.create(LocalizationApi::class.java)
}
