package dev.appolon.homelab.arr.api.sonarr

import retrofit2.Retrofit

/**
 * Creates every system- and settings-tag Sonarr API from a shared [Retrofit]
 * instance. The connection layer in :arr-data supplies base URL, API key
 * header and auth.
 */
object SonarrSystemApis {
    fun apiInfo(retrofit: Retrofit): ApiInfoApi = retrofit.create(ApiInfoApi::class.java)
    fun authentication(retrofit: Retrofit): AuthenticationApi =
        retrofit.create(AuthenticationApi::class.java)
    fun autoTagging(retrofit: Retrofit): AutoTaggingApi =
        retrofit.create(AutoTaggingApi::class.java)
    fun backup(retrofit: Retrofit): BackupApi = retrofit.create(BackupApi::class.java)
    fun diskSpace(retrofit: Retrofit): DiskSpaceApi = retrofit.create(DiskSpaceApi::class.java)
    fun fileSystem(retrofit: Retrofit): FileSystemApi = retrofit.create(FileSystemApi::class.java)
    fun health(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)
    fun hostConfig(retrofit: Retrofit): HostConfigApi = retrofit.create(HostConfigApi::class.java)
    fun localization(retrofit: Retrofit): LocalizationApi =
        retrofit.create(LocalizationApi::class.java)
    fun log(retrofit: Retrofit): LogApi = retrofit.create(LogApi::class.java)
    fun logFile(retrofit: Retrofit): LogFileApi = retrofit.create(LogFileApi::class.java)
    fun mediaManagementConfig(retrofit: Retrofit): MediaManagementConfigApi =
        retrofit.create(MediaManagementConfigApi::class.java)
    fun metadata(retrofit: Retrofit): MetadataApi = retrofit.create(MetadataApi::class.java)
    fun namingConfig(retrofit: Retrofit): NamingConfigApi =
        retrofit.create(NamingConfigApi::class.java)
    fun rootFolder(retrofit: Retrofit): RootFolderApi = retrofit.create(RootFolderApi::class.java)
    fun system(retrofit: Retrofit): SystemApi = retrofit.create(SystemApi::class.java)
    fun tag(retrofit: Retrofit): TagApi = retrofit.create(TagApi::class.java)
    fun tagDetails(retrofit: Retrofit): TagDetailsApi = retrofit.create(TagDetailsApi::class.java)
    fun task(retrofit: Retrofit): TaskApi = retrofit.create(TaskApi::class.java)
    fun uiConfig(retrofit: Retrofit): UiConfigApi = retrofit.create(UiConfigApi::class.java)
    fun update(retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)
    fun updateLogFile(retrofit: Retrofit): UpdateLogFileApi =
        retrofit.create(UpdateLogFileApi::class.java)
}
