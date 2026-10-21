package dev.appolon.homelab.arr.api.lidarr

import retrofit2.Retrofit

/** Queue, download, history and release clients sharing the supplied server configuration. */
object LidarrQueueApis {
    fun queue(retrofit: Retrofit): QueueApi = retrofit.create(QueueApi::class.java)
    fun queueAction(retrofit: Retrofit): QueueActionApi = retrofit.create(QueueActionApi::class.java)
    fun queueDetails(retrofit: Retrofit): QueueDetailsApi = retrofit.create(QueueDetailsApi::class.java)
    fun queueStatus(retrofit: Retrofit): QueueStatusApi = retrofit.create(QueueStatusApi::class.java)
    fun blocklist(retrofit: Retrofit): BlocklistApi = retrofit.create(BlocklistApi::class.java)
    fun history(retrofit: Retrofit): HistoryApi = retrofit.create(HistoryApi::class.java)
    fun command(retrofit: Retrofit): CommandApi = retrofit.create(CommandApi::class.java)
    fun manualImport(retrofit: Retrofit): ManualImportApi = retrofit.create(ManualImportApi::class.java)
    fun release(retrofit: Retrofit): ReleaseApi = retrofit.create(ReleaseApi::class.java)
    fun releasePush(retrofit: Retrofit): ReleasePushApi = retrofit.create(ReleasePushApi::class.java)
    fun parse(retrofit: Retrofit): ParseApi = retrofit.create(ParseApi::class.java)
    fun ping(retrofit: Retrofit): PingApi = retrofit.create(PingApi::class.java)
}
