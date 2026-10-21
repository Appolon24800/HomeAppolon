package dev.appolon.homelab.arr.api.radarr

import retrofit2.Retrofit

/** Builds the system, settings, and auth clients on a supplied connection. */
object RadarrSystemApis {
    fun apiInfo(retrofit: Retrofit): ApiInfoApi =
        retrofit.create(ApiInfoApi::class.java)

    fun authentication(retrofit: Retrofit): AuthenticationApi =
        retrofit.create(AuthenticationApi::class.java)

    fun autoTagging(retrofit: Retrofit): AutoTaggingApi =
        retrofit.create(AutoTaggingApi::class.java)

    fun backup(retrofit: Retrofit): BackupApi =
        retrofit.create(BackupApi::class.java)

    fun diskSpace(retrofit: Retrofit): DiskSpaceApi =
        retrofit.create(DiskSpaceApi::class.java)

    fun fileSystem(retrofit: Retrofit): FileSystemApi =
        retrofit.create(FileSystemApi::class.java)

    fun health(retrofit: Retrofit): HealthApi =
        retrofit.create(HealthApi::class.java)

    fun hostConfig(retrofit: Retrofit): HostConfigApi =
        retrofit.create(HostConfigApi::class.java)

    fun localization(retrofit: Retrofit): LocalizationApi =
        retrofit.create(LocalizationApi::class.java)

    fun log(retrofit: Retrofit): LogApi =
        retrofit.create(LogApi::class.java)

    fun logFile(retrofit: Retrofit): LogFileApi =
        retrofit.create(LogFileApi::class.java)

    fun mediaManagementConfig(retrofit: Retrofit): MediaManagementConfigApi =
        retrofit.create(MediaManagementConfigApi::class.java)

    fun metadata(retrofit: Retrofit): MetadataApi =
        retrofit.create(MetadataApi::class.java)

    fun metadataConfig(retrofit: Retrofit): MetadataConfigApi =
        retrofit.create(MetadataConfigApi::class.java)

    fun namingConfig(retrofit: Retrofit): NamingConfigApi =
        retrofit.create(NamingConfigApi::class.java)

    fun parse(retrofit: Retrofit): ParseApi =
        retrofit.create(ParseApi::class.java)

    fun ping(retrofit: Retrofit): PingApi =
        retrofit.create(PingApi::class.java)

    fun rootFolder(retrofit: Retrofit): RootFolderApi =
        retrofit.create(RootFolderApi::class.java)

    fun system(retrofit: Retrofit): SystemApi =
        retrofit.create(SystemApi::class.java)

    fun tag(retrofit: Retrofit): TagApi =
        retrofit.create(TagApi::class.java)

    fun tagDetails(retrofit: Retrofit): TagDetailsApi =
        retrofit.create(TagDetailsApi::class.java)

    fun task(retrofit: Retrofit): TaskApi =
        retrofit.create(TaskApi::class.java)

    fun uiConfig(retrofit: Retrofit): UiConfigApi =
        retrofit.create(UiConfigApi::class.java)

    fun update(retrofit: Retrofit): UpdateApi =
        retrofit.create(UpdateApi::class.java)

    fun updateLogFile(retrofit: Retrofit): UpdateLogFileApi =
        retrofit.create(UpdateLogFileApi::class.java)
}
