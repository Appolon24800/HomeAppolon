package dev.appolon.homelab.arr.api.radarr

import retrofit2.Retrofit

/** Builds the queue, history, import, and command clients on a supplied connection. */
object RadarrQueueApis {
    fun queue(retrofit: Retrofit): QueueApi =
        retrofit.create(QueueApi::class.java)

    fun queueAction(retrofit: Retrofit): QueueActionApi =
        retrofit.create(QueueActionApi::class.java)

    fun queueDetails(retrofit: Retrofit): QueueDetailsApi =
        retrofit.create(QueueDetailsApi::class.java)

    fun queueStatus(retrofit: Retrofit): QueueStatusApi =
        retrofit.create(QueueStatusApi::class.java)

    fun blocklist(retrofit: Retrofit): BlocklistApi =
        retrofit.create(BlocklistApi::class.java)

    fun history(retrofit: Retrofit): HistoryApi =
        retrofit.create(HistoryApi::class.java)

    fun manualImport(retrofit: Retrofit): ManualImportApi =
        retrofit.create(ManualImportApi::class.java)

    fun command(retrofit: Retrofit): CommandApi =
        retrofit.create(CommandApi::class.java)

}
