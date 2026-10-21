package dev.appolon.homelab.arr.feature.bazarr.settings.data

import dev.appolon.homelab.arr.api.bazarr.ProvidersApi
import dev.appolon.homelab.arr.api.bazarr.SystemBackupsApi
import dev.appolon.homelab.arr.api.bazarr.SystemJobsApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesProfilesApi
import dev.appolon.homelab.arr.api.bazarr.SystemLogsApi
import dev.appolon.homelab.arr.api.bazarr.SystemSettingsApi
import dev.appolon.homelab.arr.api.bazarr.SystemStatusApi
import dev.appolon.homelab.arr.api.bazarr.SystemTasksApi
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.idParamOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.urlEncoded
import dev.appolon.homelab.arr.feature.bazarr.history.data.paramValueOf
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrBackupFile
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrJobRow
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLanguageProfile
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLanguageSetting
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLogEntry
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrProviderStatus
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrScheduledTask
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrStatusRow
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toBackupFiles
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toJobRows
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toLanguageProfiles
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toLanguageSettings
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toLogEntries
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toProviderStatuses
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toScheduledTasks
import dev.appolon.homelab.arr.feature.bazarr.settings.model.toStatusRows
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import retrofit2.Response

/** Outcome of a settings write: applied on the server, or queued for replay. */
sealed interface SettingsWriteOutcome {
    data object Synced : SettingsWriteOutcome
    data class Queued(val actionId: ActionId) : SettingsWriteOutcome
}

/**
 * Repository for the Bazarr settings screens. Every section reads
 * cache-first through the [EntityStore] with a fetchedAt TTL; loaders take a
 * [ttlMillis] argument and the screens pass [BazarrSettingsRepository.FORCE_REFRESH]
 * on pull-to-refresh. Writes hit the server first and, when the failure is
 * retryable (offline or network), enqueue a replayable [QueuedAction] while
 * the cache updates optimistically and the affected row shows the queued
 * indicator. The webhook test is a diagnostic probe, so it never queues.
 * No function throws.
 */
class BazarrSettingsRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val languagesApi: SystemLanguagesApi,
    private val languageProfilesApi: SystemLanguagesProfilesApi,
    private val providersApi: ProvidersApi,
    private val webhookSettingsApi: SystemSettingsApi,
    private val tasksApi: SystemTasksApi,
    private val jobsApi: SystemJobsApi,
    private val statusApi: SystemStatusApi,
    private val backupsApi: SystemBackupsApi,
    private val logsApi: SystemLogsApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---------------------------------------------------------------- reads

    /** Live language rows, enabled languages first, then alphabetical. */
    fun observeLanguages(): Flow<List<BazarrLanguageSetting>> =
        store.observe(serverId, LANGUAGE_TYPE, BazarrLanguageSetting.serializer())
            .map { rows ->
                rows.sortedWith(
                    compareByDescending<BazarrLanguageSetting> { it.enabled }.thenBy { it.label },
                )
            }

    /** Live language profiles, alphabetical by name. */
    fun observeProfiles(): Flow<List<BazarrLanguageProfile>> =
        store.observe(serverId, PROFILE_TYPE, BazarrLanguageProfile.serializer())
            .map { rows -> rows.sortedBy { it.label } }

    /** Live provider health rows, alphabetical by provider name. */
    fun observeProviders(): Flow<List<BazarrProviderStatus>> =
        store.observe(serverId, PROVIDER_TYPE, BazarrProviderStatus.serializer())
            .map { rows -> rows.sortedBy { it.provider } }

    /** Live scheduler tasks, alphabetical. */
    fun observeTasks(): Flow<List<BazarrScheduledTask>> =
        store.observe(serverId, TASK_TYPE, BazarrScheduledTask.serializer())
            .map { rows -> rows.sortedBy { it.label } }

    /** Live job queue rows in queue order (ascending server job id). */
    fun observeJobs(): Flow<List<BazarrJobRow>> =
        store.observe(serverId, JOB_TYPE, BazarrJobRow.serializer())
            .map { rows -> rows.sortedBy { it.id } }

    /** Live status rows, alphabetical by key for a stable settings layout. */
    fun observeStatus(): Flow<List<BazarrStatusRow>> =
        store.observe(serverId, STATUS_TYPE, BazarrStatusRow.serializer())
            .map { rows -> rows.sortedBy { it.key } }

    /** Live backup files, newest first by the server-formatted date. */
    fun observeBackups(): Flow<List<BazarrBackupFile>> =
        store.observe(serverId, BACKUP_TYPE, BazarrBackupFile.serializer())
            .map { rows -> rows.sortedByDescending { it.date } }

    /** Live log entries, newest first. */
    fun observeLogs(): Flow<List<BazarrLogEntry>> =
        store.observe(serverId, LOG_TYPE, BazarrLogEntry.serializer())
            .map { rows -> rows.sortedByDescending { it.timestampMillis } }

    /**
     * Cache-first section loader shared by every read: serves the store when
     * fresh, otherwise fetches, parses, and replaces the cache. A parse that
     * yields no rows keeps the previous cache so a glitched payload never
     * blanks a settings screen.
     */
    private suspend fun <T> loadSection(
        type: EntityType,
        ttlMillis: Long,
        serializer: KSerializer<T>,
        apiCall: suspend () -> JsonObject,
        parse: (JsonObject) -> List<T>,
        ids: (List<T>) -> List<Pair<String, T>>,
    ): ArrResult<Unit> = withContext(ioDispatcher) {
        if (ttlMillis > 0) {
            val cached = store.getAll(serverId, type, serializer)
            val fresh = store.lastFetchedAt(serverId, type)
                ?.let { now() - it <= ttlMillis } == true
            if (cached.isNotEmpty() && fresh) return@withContext ArrResult.ok(Unit)
        }
        val fetched = try {
            apiCall()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val rows = parse(fetched)
        if (rows.isNotEmpty()) {
            store.deleteAll(serverId, type)
            store.putAll(serverId, type, ids(rows), serializer, fetchedAtMillis = now())
        }
        ArrResult.ok(Unit)
    }

    /** Loads the enabled-language settings list under [ttlMillis]. */
    suspend fun languages(ttlMillis: Long = LANGUAGES_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = LANGUAGE_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrLanguageSetting.serializer(),
        apiCall = { languagesApi.getLanguages(history = null) },
        parse = { it.toLanguageSettings() },
        ids = { rows -> rows.map { row -> row.code2 to row } },
    )

    /** Loads the language profiles with cutoff and release rules. */
    suspend fun profiles(ttlMillis: Long = LANGUAGES_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = PROFILE_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrLanguageProfile.serializer(),
        apiCall = { languageProfilesApi.getLanguagesProfiles() },
        parse = { it.toLanguageProfiles() },
        ids = { rows -> rows.map { row -> row.profileId.toString() to row } },
    )

    /** Loads provider health including any dynamic fields the server adds. */
    suspend fun providers(ttlMillis: Long = STATUS_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = PROVIDER_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrProviderStatus.serializer(),
        apiCall = { providersApi.getProviders(history = null) },
        parse = { it.toProviderStatuses() },
        ids = { rows -> rows.map { row -> row.provider to row } },
    )

    /** Loads scheduler tasks with interval and next run. */
    suspend fun tasks(ttlMillis: Long = STATUS_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = TASK_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrScheduledTask.serializer(),
        apiCall = { tasksApi.getSystemTasks() },
        parse = { it.toScheduledTasks() },
        ids = { rows -> rows.map { row -> row.jobId to row } },
    )

    /** Loads the pending job queue. */
    suspend fun jobs(ttlMillis: Long = VOLATILE_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = JOB_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrJobRow.serializer(),
        apiCall = { jobsApi.getSystemJobs(id = null, status = null) },
        parse = { it.toJobRows() },
        ids = { rows -> rows.map { row -> row.id.toString() to row } },
    )

    /** Loads the flattened system status rows. */
    suspend fun status(ttlMillis: Long = STATUS_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = STATUS_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrStatusRow.serializer(),
        apiCall = { statusApi.getSystemStatus() },
        parse = { it.toStatusRows() },
        ids = { rows -> rows.map { row -> row.key to row } },
    )

    /** Loads the backup file list. */
    suspend fun backups(ttlMillis: Long = STATUS_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = BACKUP_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrBackupFile.serializer(),
        apiCall = { backupsApi.getSystemBackups() },
        parse = { it.toBackupFiles() },
        ids = { rows -> rows.map { row -> row.filename to row } },
    )

    /** Loads the log tail. */
    suspend fun logs(ttlMillis: Long = VOLATILE_TTL_MILLIS): ArrResult<Unit> = loadSection(
        type = LOG_TYPE,
        ttlMillis = ttlMillis,
        serializer = BazarrLogEntry.serializer(),
        apiCall = { logsApi.getSystemLogs() },
        parse = { it.toLogEntries() },
        ids = { rows -> rows.mapIndexed { index, row -> "${row.timestampMillis}_$index" to row } },
    )

    // --------------------------------------------------------------- writes

    /**
     * Asks Bazarr to drop every provider counter and retry state. No row
     * mutation is possible, so the queued path signals through the
     * whole-screen queued indicator.
     */
    suspend fun resetProviders(): ArrResult<SettingsWriteOutcome> = write(
        method = "POST",
        path = "api/providers?action=reset",
        description = "Reset subtitle providers",
        call = { providersApi.postProviders(action = "reset") },
    )

    /**
     * Runs one scheduled task now. Both the synced and queued paths mark the
     * cached row running so the scheduler reflects the request immediately.
     * The queued descriptor carries the real `api/system/tasks` path the
     * Retrofit API calls, so replay hits the same endpoint.
     */
    suspend fun runTaskNow(task: BazarrScheduledTask): ArrResult<SettingsWriteOutcome> = write(
        method = "POST",
        path = "api/system/tasks?taskid=${task.jobId.urlEncoded()}",
        description = "Run scheduled task",
        call = { tasksApi.postSystemTasks(taskId = task.jobId) },
        optimistic = {
            store.put(
                serverId,
                TASK_TYPE,
                task.jobId,
                task.copy(running = true),
                BazarrScheduledTask.serializer(),
            )
        },
    )

    /** Force starts, or moves a job to the top or bottom of its queue. */
    suspend fun jobAction(id: Int, action: String): ArrResult<SettingsWriteOutcome> = write(
        method = "POST",
        path = "api/system/jobs?id=$id&action=${action.urlEncoded()}",
        description = "Update job queue",
        call = { jobsApi.postSystemJobs(id = id, action = action) },
    )

    /**
     * Empties one job queue. Both paths drop the queue's cached rows so the
     * list reflects the request immediately.
     */
    suspend fun emptyJobQueue(queueName: String): ArrResult<SettingsWriteOutcome> = write(
        method = "PATCH",
        path = "api/system/jobs?queueName=${queueName.urlEncoded()}",
        description = "Empty job queue",
        call = { jobsApi.patchSystemJobs(queueName = queueName) },
        optimistic = {
            store.getAll(serverId, JOB_TYPE, BazarrJobRow.serializer())
                .filter { it.queueName == queueName }
                .forEach { store.delete(serverId, JOB_TYPE, it.id.toString()) }
        },
    )

    /** Removes one job from its queue; the cached row drops immediately. */
    suspend fun deleteJob(job: BazarrJobRow): ArrResult<SettingsWriteOutcome> = write(
        method = "DELETE",
        path = "api/system/jobs?id=${job.id}",
        description = "Delete queued job",
        call = { jobsApi.deleteSystemJobs(id = job.id) },
        optimistic = { store.delete(serverId, JOB_TYPE, job.id.toString()) },
    )

    /** Starts a backup on the server; the file list fills in on next refresh. */
    suspend fun createBackup(): ArrResult<SettingsWriteOutcome> = write(
        method = "POST",
        path = "api/system/backups",
        description = "Create backup",
        call = { backupsApi.postSystemBackups() },
    )

    /** Restores one backup file; Bazarr restarts itself while applying it. */
    suspend fun restoreBackup(filename: String): ArrResult<SettingsWriteOutcome> = write(
        method = "PATCH",
        path = "api/system/backups?filename=${filename.urlEncoded()}",
        description = "Restore backup",
        call = { backupsApi.patchSystemBackups(filename = filename) },
    )

    /** Deletes one backup file; the cached row drops immediately. */
    suspend fun deleteBackup(backup: BazarrBackupFile): ArrResult<SettingsWriteOutcome> = write(
        method = "DELETE",
        path = "api/system/backups?filename=${backup.filename.urlEncoded()}",
        description = "Delete backup",
        call = { backupsApi.deleteSystemBackups(filename = backup.filename) },
        optimistic = { store.delete(serverId, BACKUP_TYPE, backup.filename) },
    )

    /**
     * Forces a log rotation. Both paths clear the cached tail because the
     * current file closes and a fresh one starts.
     */
    suspend fun rotateLogs(): ArrResult<SettingsWriteOutcome> = write(
        method = "DELETE",
        path = "api/system/logs",
        description = "Rotate log files",
        call = { logsApi.deleteSystemLogs() },
        optimistic = { store.deleteAll(serverId, LOG_TYPE) },
    )

    /**
     * Probes the external webhook connection. Diagnostic only: the outcome
     * describes the server's reachability right now, so an offline failure
     * surfaces as an error instead of queueing a stale replay.
     */
    suspend fun testWebhook(): ArrResult<Unit> = withContext(ioDispatcher) {
        try {
            val response = webhookSettingsApi.postSystemWebhookTest()
            if (response.isSuccessful) {
                ArrResult.ok(Unit)
            } else {
                ArrResult.err(HttpException(response).toArrError())
            }
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    // ------------------------------------------------------- queued signals

    /** True while a provider reset waits for replay. */
    fun queuedProviderReset(): Flow<Boolean> = actionQueue.observe().map { pending ->
        pending.any { it.serverId == serverId && it.path == "api/providers?action=reset" }
    }

    /** Task ids this server has queued run-now actions for. */
    fun queuedTaskIds(): Flow<Set<String>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path.startsWith("api/system/tasks") }
            .mapNotNull { paramValueOf(it.path, "taskid") }
            .toSet()
    }

    /** Job ids this server has queued update or delete actions for. */
    fun queuedJobIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { action ->
                action.serverId == serverId &&
                    (action.method == "POST" || action.method == "DELETE") &&
                    action.path.startsWith("api/system/jobs")
            }
            .flatMap { idParamOf(it.path, "id").asSequence() }
            .toSet()
    }

    /** Backup filenames this server has queued restore or delete actions for. */
    fun queuedBackupFiles(): Flow<Set<String>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path.startsWith("api/system/backups") }
            .mapNotNull { paramValueOf(it.path, "filename") }
            .toSet()
    }

    /** True while a log rotation waits for replay. */
    fun queuedLogRotation(): Flow<Boolean> = actionQueue.observe().map { pending ->
        pending.any {
            it.serverId == serverId && it.method == "DELETE" && it.path == "api/system/logs"
        }
    }

    // ------------------------------------------------------------ internals

    private suspend fun write(
        method: String,
        path: String,
        description: String,
        call: suspend () -> Response<Unit>,
        optimistic: suspend () -> Unit = {},
    ): ArrResult<SettingsWriteOutcome> = withContext(ioDispatcher) {
        try {
            val response = call()
            if (response.isSuccessful) {
                optimistic()
                ArrResult.ok(SettingsWriteOutcome.Synced)
            } else {
                ArrResult.err(HttpException(response).toArrError())
            }
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), method, path, description, optimistic)
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), method, path, description, optimistic)
        }
    }

    private suspend fun enqueueOrErr(
        error: ArrError,
        method: String,
        path: String,
        description: String,
        optimistic: suspend () -> Unit,
    ): ArrResult<SettingsWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        optimistic()
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.BAZARR,
            method = method,
            path = path,
            description = description,
            enqueuedAtMillis = now(),
        )
        return try {
            actionQueue.enqueue(action)
            ArrResult.ok(SettingsWriteOutcome.Queued(action.actionId))
        } catch (e: Throwable) {
            ArrResult.err(error)
        }
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    public companion object {
        internal val LANGUAGE_TYPE = EntityType("bazarr.settings.language")
        internal val PROFILE_TYPE = EntityType("bazarr.settings.profile")
        internal val PROVIDER_TYPE = EntityType("bazarr.settings.provider")
        internal val TASK_TYPE = EntityType("bazarr.settings.task")
        internal val JOB_TYPE = EntityType("bazarr.settings.job")
        internal val STATUS_TYPE = EntityType("bazarr.settings.status")
        internal val BACKUP_TYPE = EntityType("bazarr.settings.backup")
        internal val LOG_TYPE = EntityType("bazarr.settings.log")

        /** Pass to any section loader to bypass the cache and re-fetch. */
        public const val FORCE_REFRESH: Long = 0

        /** One day: languages and profiles change only with settings edits. */
        public const val LANGUAGES_TTL_MILLIS: Long = 24 * 60 * 60 * 1000

        /** Five minutes: provider health, tasks, status, and backups drift slowly. */
        public const val STATUS_TTL_MILLIS: Long = 5 * 60 * 1000

        /** One minute: the job queue and log tail move fast while screens are open. */
        public const val VOLATILE_TTL_MILLIS: Long = 60 * 1000
    }
}
