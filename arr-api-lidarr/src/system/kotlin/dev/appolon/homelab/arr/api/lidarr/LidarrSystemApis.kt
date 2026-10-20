package dev.appolon.homelab.arr.api.lidarr

import retrofit2.Retrofit

/**
 * Factory for every system, settings and connect client this group owns:
 * status, tasks, backups, updates, logs, health, auth, config, providers,
 * tags, root folders, remote path mappings and the file system browser.
 * Ping and Parse clients live with the queue and release groups.
 */
object LidarrSystemApis {
    fun apiInfo(retrofit: Retrofit): ApiInfoApi = retrofit.create(ApiInfoApi::class.java)
    fun authentication(retrofit: Retrofit): AuthenticationApi =
        retrofit.create(AuthenticationApi::class.java)
    fun backup(retrofit: Retrofit): BackupApi = retrofit.create(BackupApi::class.java)
    fun diskSpace(retrofit: Retrofit): DiskSpaceApi = retrofit.create(DiskSpaceApi::class.java)
    fun health(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)
    fun log(retrofit: Retrofit): LogApi = retrofit.create(LogApi::class.java)
    fun logFile(retrofit: Retrofit): LogFileApi = retrofit.create(LogFileApi::class.java)
    fun system(retrofit: Retrofit): SystemApi = retrofit.create(SystemApi::class.java)
    fun task(retrofit: Retrofit): TaskApi = retrofit.create(TaskApi::class.java)
    fun update(retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)
    fun updateLogFile(retrofit: Retrofit): UpdateLogFileApi =
        retrofit.create(UpdateLogFileApi::class.java)
    fun localization(retrofit: Retrofit): LocalizationApi =
        retrofit.create(LocalizationApi::class.java)

    fun autoTagging(retrofit: Retrofit): AutoTaggingApi =
        retrofit.create(AutoTaggingApi::class.java)
    fun hostConfig(retrofit: Retrofit): HostConfigApi =
        retrofit.create(HostConfigApi::class.java)
    fun mediaManagementConfig(retrofit: Retrofit): MediaManagementConfigApi =
        retrofit.create(MediaManagementConfigApi::class.java)
    fun namingConfig(retrofit: Retrofit): NamingConfigApi =
        retrofit.create(NamingConfigApi::class.java)
    fun uiConfig(retrofit: Retrofit): UiConfigApi = retrofit.create(UiConfigApi::class.java)
    fun metadata(retrofit: Retrofit): MetadataApi = retrofit.create(MetadataApi::class.java)
    fun notification(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)
    fun downloadClient(retrofit: Retrofit): DownloadClientApi =
        retrofit.create(DownloadClientApi::class.java)
    fun downloadClientConfig(retrofit: Retrofit): DownloadClientConfigApi =
        retrofit.create(DownloadClientConfigApi::class.java)
    fun indexer(retrofit: Retrofit): IndexerApi = retrofit.create(IndexerApi::class.java)
    fun indexerConfig(retrofit: Retrofit): IndexerConfigApi =
        retrofit.create(IndexerConfigApi::class.java)
    fun indexerFlag(retrofit: Retrofit): IndexerFlagApi =
        retrofit.create(IndexerFlagApi::class.java)
    fun remotePathMapping(retrofit: Retrofit): RemotePathMappingApi =
        retrofit.create(RemotePathMappingApi::class.java)
    fun rootFolder(retrofit: Retrofit): RootFolderApi =
        retrofit.create(RootFolderApi::class.java)
    fun tag(retrofit: Retrofit): TagApi = retrofit.create(TagApi::class.java)
    fun tagDetails(retrofit: Retrofit): TagDetailsApi =
        retrofit.create(TagDetailsApi::class.java)
    fun fileSystem(retrofit: Retrofit): FileSystemApi =
        retrofit.create(FileSystemApi::class.java)
}
