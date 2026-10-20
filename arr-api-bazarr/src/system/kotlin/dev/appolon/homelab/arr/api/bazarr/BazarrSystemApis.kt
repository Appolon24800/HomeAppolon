package dev.appolon.homelab.arr.api.bazarr

import retrofit2.Retrofit

/**
 * Creates every system and settings tagged interface from one configured [Retrofit] instance,
 * which supplies the base URL (including the /api prefix), X-Api-Key header and JSON converter.
 */
object BazarrSystemApis {
    fun announcements(retrofit: Retrofit): SystemAnnouncementsApi =
        retrofit.create(SystemAnnouncementsApi::class.java)

    fun backups(retrofit: Retrofit): SystemBackupsApi =
        retrofit.create(SystemBackupsApi::class.java)

    fun health(retrofit: Retrofit): SystemHealthApi =
        retrofit.create(SystemHealthApi::class.java)

    fun jobs(retrofit: Retrofit): SystemJobsApi =
        retrofit.create(SystemJobsApi::class.java)

    fun languages(retrofit: Retrofit): SystemLanguagesApi =
        retrofit.create(SystemLanguagesApi::class.java)

    fun languageProfiles(retrofit: Retrofit): SystemLanguagesProfilesApi =
        retrofit.create(SystemLanguagesProfilesApi::class.java)

    fun logs(retrofit: Retrofit): SystemLogsApi =
        retrofit.create(SystemLogsApi::class.java)

    fun ping(retrofit: Retrofit): SystemPingApi =
        retrofit.create(SystemPingApi::class.java)

    fun releases(retrofit: Retrofit): SystemReleasesApi =
        retrofit.create(SystemReleasesApi::class.java)

    fun status(retrofit: Retrofit): SystemStatusApi =
        retrofit.create(SystemStatusApi::class.java)

    fun tasks(retrofit: Retrofit): SystemTasksApi =
        retrofit.create(SystemTasksApi::class.java)

    fun settings(retrofit: Retrofit): SystemSettingsApi =
        retrofit.create(SystemSettingsApi::class.java)
}
