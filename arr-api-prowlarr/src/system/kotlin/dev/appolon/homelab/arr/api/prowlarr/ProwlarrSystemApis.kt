package dev.appolon.homelab.arr.api.prowlarr

import retrofit2.Retrofit

/**
 * Creates every system-group interface from a connection-layer Retrofit
 * instance that already carries the base URL with path prefix, X-Api-Key and
 * the shared ArrJson converter.
 */
object ProwlarrSystemApis {
    fun system(retrofit: Retrofit): SystemApi = retrofit.create(SystemApi::class.java)
    fun backup(retrofit: Retrofit): BackupApi = retrofit.create(BackupApi::class.java)
    fun health(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)
    fun log(retrofit: Retrofit): LogApi = retrofit.create(LogApi::class.java)
    fun logFile(retrofit: Retrofit): LogFileApi = retrofit.create(LogFileApi::class.java)
    fun update(retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)
    fun updateLogFile(retrofit: Retrofit): UpdateLogFileApi =
        retrofit.create(UpdateLogFileApi::class.java)
    fun task(retrofit: Retrofit): TaskApi = retrofit.create(TaskApi::class.java)
    fun ping(retrofit: Retrofit): PingApi = retrofit.create(PingApi::class.java)
    fun apiInfo(retrofit: Retrofit): ApiInfoApi = retrofit.create(ApiInfoApi::class.java)
    fun staticResource(retrofit: Retrofit): StaticResourceApi =
        retrofit.create(StaticResourceApi::class.java)
}
