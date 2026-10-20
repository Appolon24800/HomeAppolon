package dev.appolon.homelab.arr.feature.radarr.settings.system.data

import dev.appolon.homelab.arr.api.radarr.AutoTaggingApi
import dev.appolon.homelab.arr.api.radarr.BackupApi
import dev.appolon.homelab.arr.api.radarr.CommandApi
import dev.appolon.homelab.arr.api.radarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.radarr.HealthApi
import dev.appolon.homelab.arr.api.radarr.HostConfigApi
import dev.appolon.homelab.arr.api.radarr.SystemApi
import dev.appolon.homelab.arr.api.radarr.TagApi
import dev.appolon.homelab.arr.api.radarr.TagDetailsApi
import dev.appolon.homelab.arr.api.radarr.TaskApi
import dev.appolon.homelab.arr.api.radarr.UpdateApi
import dev.appolon.homelab.arr.api.radarr.UpdateLogFileApi
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.BackupResource
import dev.appolon.homelab.arr.api.radarr.model.BackupRestoreResource
import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.radarr.model.LogFileResource
import dev.appolon.homelab.arr.api.radarr.model.SystemResource
import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.api.radarr.model.TaskResource
import dev.appolon.homelab.arr.api.radarr.model.UpdateResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import retrofit2.Response

/**
 * Cache-first repository for the general, system, backup, updates, and
 * tags screen group: the host config (host, security, logging, analytics,
 * proxy display, backup schedule), backups, updates and their log files,
 * system status with tasks, disk space, and health, tags with per-tag
 * usage, and auto tagging rules with their schema.
 *
 * Reads come from the [EntityStore] with a fetchedAt TTL. Writes hit the
 * server first; when the network is unreachable the change applies
 * optimistically to the cache and enqueues for replay through the
 * [ActionQueue]. Secret-bearing host config fields (password, api key,
 * certificate and proxy credentials) are stripped before anything is
 * cached or queued; a queued PUT omits them, which the server reads as
 * "unchanged". Nothing throws; everything runs on [ioContext].
 */
class RadarrSettingsSystemRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val hostConfigApi: HostConfigApi,
    private val backupApi: BackupApi,
    private val updateApi: UpdateApi,
    private val updateLogFileApi: UpdateLogFileApi,
    private val systemApi: SystemApi,
    private val taskApi: TaskApi,
    private val diskSpaceApi: DiskSpaceApi,
    private val healthApi: HealthApi,
    private val tagApi: TagApi,
    private val tagDetailsApi: TagDetailsApi,
    private val autoTaggingApi: AutoTaggingApi,
    private val commandApi: CommandApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    private val tempIds = AtomicLong(TEMP_ID_BASE)

    // -------------------------------------------------------------- observe

    fun observeHostConfig(): Flow<HostConfigResource?> =
        store.observe(serverId, HOST_CONFIG_TYPE, HostConfigResource.serializer())
            .map { it.firstOrNull() }

    fun observeBackups(): Flow<List<BackupResource>> =
        store.observe(serverId, BACKUP_TYPE, BackupResource.serializer())

    fun observeUpdates(): Flow<List<UpdateResource>> =
        store.observe(serverId, UPDATE_TYPE, UpdateResource.serializer())

    fun observeSystemStatus(): Flow<SystemResource?> =
        store.observe(serverId, SYSTEM_STATUS_TYPE, SystemResource.serializer())
            .map { it.firstOrNull() }

    fun observeTasks(): Flow<List<TaskResource>> =
        store.observe(serverId, TASK_TYPE, TaskResource.serializer())

    fun observeDiskSpace(): Flow<List<DiskSpaceResource>> =
        store.observe(serverId, DISK_SPACE_TYPE, DiskSpaceResource.serializer())

    fun observeHealth(): Flow<List<HealthResource>> =
        store.observe(serverId, HEALTH_TYPE, HealthResource.serializer())

    fun observeTags(): Flow<List<TagResource>> =
        store.observe(serverId, TAG_TYPE, TagResource.serializer())

    fun observeTagDetails(): Flow<List<TagDetailsResource>> =
        store.observe(serverId, TAG_DETAILS_TYPE, TagDetailsResource.serializer())

    fun observeAutoTagging(): Flow<List<AutoTaggingResource>> =
        store.observe(serverId, AUTO_TAGGING_TYPE, AutoTaggingResource.serializer())

    fun observeUpdateLogFiles(): Flow<List<LogFileResource>> =
        store.observe(serverId, UPDATE_LOG_FILE_TYPE, LogFileResource.serializer())

    // --------------------------------------------------------------- cached

    suspend fun cachedHostConfig(): HostConfigResource? =
        store.getAll(serverId, HOST_CONFIG_TYPE, HostConfigResource.serializer()).firstOrNull()

    suspend fun cachedBackups(): List<BackupResource> =
        store.getAll(serverId, BACKUP_TYPE, BackupResource.serializer())

    suspend fun cachedUpdates(): List<UpdateResource> =
        store.getAll(serverId, UPDATE_TYPE, UpdateResource.serializer())

    suspend fun cachedSystemStatus(): SystemResource? =
        store.get(serverId, SYSTEM_STATUS_TYPE, SINGLE_ID, SystemResource.serializer())

    suspend fun cachedTasks(): List<TaskResource> =
        store.getAll(serverId, TASK_TYPE, TaskResource.serializer())

    suspend fun cachedDiskSpace(): List<DiskSpaceResource> =
        store.getAll(serverId, DISK_SPACE_TYPE, DiskSpaceResource.serializer())

    suspend fun cachedHealth(): List<HealthResource> =
        store.getAll(serverId, HEALTH_TYPE, HealthResource.serializer())

    suspend fun cachedTags(): List<TagResource> =
        store.getAll(serverId, TAG_TYPE, TagResource.serializer())

    suspend fun cachedTagDetails(): List<TagDetailsResource> =
        store.getAll(serverId, TAG_DETAILS_TYPE, TagDetailsResource.serializer())

    suspend fun cachedAutoTagging(): List<AutoTaggingResource> =
        store.getAll(serverId, AUTO_TAGGING_TYPE, AutoTaggingResource.serializer())

    suspend fun cachedAutoTaggingSchema(): List<AutoTaggingSpecificationSchema> =
        store.getAll(serverId, AUTO_TAGGING_SCHEMA_TYPE, AutoTaggingSpecificationSchema.serializer())
            .sortedBy { it.implementationName ?: it.name ?: "" }

    suspend fun cachedUpdateLogFiles(): List<LogFileResource> =
        store.getAll(serverId, UPDATE_LOG_FILE_TYPE, LogFileResource.serializer())

    // -------------------------------------------------------------- refresh

    /** True when the host config cache is absent or past its TTL. */
    suspend fun isGeneralStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): Boolean =
        isTypeStale(HOST_CONFIG_TYPE, ttlMillis)

    suspend fun isBackupStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): Boolean =
        isTypeStale(BACKUP_TYPE, ttlMillis)

    suspend fun isSystemStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): Boolean =
        listOf(SYSTEM_STATUS_TYPE, TASK_TYPE, DISK_SPACE_TYPE, HEALTH_TYPE)
            .any { isTypeStale(it, ttlMillis) }

    suspend fun isTagStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): Boolean =
        listOf(TAG_TYPE, TAG_DETAILS_TYPE).any { isTypeStale(it, ttlMillis) }

    suspend fun isAutoTaggingStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): Boolean =
        isTypeStale(AUTO_TAGGING_TYPE, ttlMillis)

    /** Re-fetches the host config and replaces its single-row cache. */
    suspend fun refreshGeneral(): ArrResult<Unit> = withContext(ioContext) {
        val config = when (val r = fetch { hostConfigApi.hostConfig() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        putFreshRow(HOST_CONFIG_TYPE, SINGLE_ID, config.strippedOfSecrets(), HostConfigResource.serializer())
        ArrResult.ok(Unit)
    }

    suspend fun refreshGeneralIfStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): ArrResult<Unit> =
        if (isGeneralStale(ttlMillis)) refreshGeneral() else ArrResult.ok(Unit)

    /**
     * Re-fetches the backup list. The host config follows along because the
     * backup screen edits the schedule rows; a failure there is ignored so
     * the list still renders from the cached schedule.
     */
    suspend fun refreshBackups(): ArrResult<Unit> = withContext(ioContext) {
        val backups = when (val r = fetch { backupApi.backups() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        replace(BACKUP_TYPE, backups.sortedByDescending { it.time }, BackupResource.serializer(), now())
        if (isGeneralStale()) {
            when (val r = fetch { hostConfigApi.hostConfig() }) {
                is ArrResult.Ok -> putFreshRow(
                    HOST_CONFIG_TYPE,
                    SINGLE_ID,
                    r.value.strippedOfSecrets(),
                    HostConfigResource.serializer(),
                )

                is ArrResult.Err -> Unit // schedule rows stay cached
            }
        }
        ArrResult.ok(Unit)
    }

    suspend fun refreshBackupsIfStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): ArrResult<Unit> =
        if (isBackupStale(ttlMillis)) refreshBackups() else ArrResult.ok(Unit)

    /** Re-fetches the update list, current status, and update log files. */
    suspend fun refreshUpdates(): ArrResult<Unit> = withContext(ioContext) {
        val updates = when (val r = fetch { updateApi.updates() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val status = when (val r = fetch { systemApi.systemStatus() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val logFiles = when (val r = fetch { updateLogFileApi.updateLogFiles() }) {
            is ArrResult.Err -> emptyList() // log files stay optional
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        replace(UPDATE_TYPE, updates.sortedByDescending { it.releaseDate }, UpdateResource.serializer(), stamp)
        putFreshRow(SYSTEM_STATUS_TYPE, SINGLE_ID, status, SystemResource.serializer())
        replace(UPDATE_LOG_FILE_TYPE, logFiles.sortedByDescending { it.lastWriteTime }, LogFileResource.serializer(), stamp)
        ArrResult.ok(Unit)
    }

    suspend fun refreshUpdatesIfStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): ArrResult<Unit> =
        if (isTypeStale(UPDATE_TYPE, ttlMillis)) refreshUpdates() else ArrResult.ok(Unit)

    /** Re-fetches status, tasks, disk space, and health as one system group. */
    suspend fun refreshSystem(): ArrResult<Unit> = withContext(ioContext) {
        val status = when (val r = fetch { systemApi.systemStatus() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val tasks = when (val r = fetch { taskApi.tasks() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val disk = when (val r = fetch { diskSpaceApi.diskSpace() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val health = when (val r = fetch { healthApi.health() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        putFreshRow(SYSTEM_STATUS_TYPE, SINGLE_ID, status, SystemResource.serializer())
        replace(TASK_TYPE, tasks.sortedBy { it.name }, TaskResource.serializer(), stamp)
        replace(DISK_SPACE_TYPE, disk.sortedBy { it.path }, DiskSpaceResource.serializer(), stamp)
        replace(HEALTH_TYPE, health.sortedBy { it.id }, HealthResource.serializer(), stamp)
        ArrResult.ok(Unit)
    }
    suspend fun refreshSystemIfStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): ArrResult<Unit> =
        if (isSystemStale(ttlMillis)) refreshSystem() else ArrResult.ok(Unit)

    /** Re-fetches tags and their usage details together. */
    suspend fun refreshTags(): ArrResult<Unit> = withContext(ioContext) {
        val tags = when (val r = fetch { tagApi.tags() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val details = when (val r = fetch { tagDetailsApi.tagDetails() }) {
            is ArrResult.Err -> emptyList() // usage rows stay optional
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        replace(TAG_TYPE, tags.sortedBy { it.label }, TagResource.serializer(), stamp)
        replace(TAG_DETAILS_TYPE, details.sortedBy { it.label }, TagDetailsResource.serializer(), stamp)
        ArrResult.ok(Unit)
    }

    suspend fun refreshTagsIfStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): ArrResult<Unit> =
        if (isTagStale(ttlMillis)) refreshTags() else ArrResult.ok(Unit)

    /** Re-fetches auto tagging rules. */
    suspend fun refreshAutoTagging(): ArrResult<Unit> = withContext(ioContext) {
        val rules = when (val r = fetch { autoTaggingApi.autoTaggings() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        replace(AUTO_TAGGING_TYPE, rules.sortedBy { it.name }, AutoTaggingResource.serializer(), now())
        ArrResult.ok(Unit)
    }

    suspend fun refreshAutoTaggingIfStale(ttlMillis: Long = SYSTEM_TTL_MILLIS): ArrResult<Unit> =
        if (isAutoTaggingStale(ttlMillis)) refreshAutoTagging() else ArrResult.ok(Unit)

    /**
     * Fetches the condition schema the editor's add-condition picker lists.
     * Cached with a long TTL; failures fall back to the cache.
     */
    suspend fun refreshAutoTaggingSchema(): ArrResult<List<AutoTaggingSpecificationSchema>> =
        withContext(ioContext) {
            if (!isTypeStale(AUTO_TAGGING_SCHEMA_TYPE, SCHEMA_TTL_MILLIS)) {
                return@withContext ArrResult.ok(cachedAutoTaggingSchema())
            }
            val schema = when (val r = fetch { autoTaggingApi.autoTaggingSchema() }) {
                is ArrResult.Err -> {
                    val cached = cachedAutoTaggingSchema()
                    return@withContext if (cached.isNotEmpty()) ArrResult.ok(cached) else r
                }

                is ArrResult.Ok -> r.value
            }
            store.putAll(
                serverId,
                AUTO_TAGGING_SCHEMA_TYPE,
                schema.map { (it.implementation ?: it.name ?: it.id.toString()) to it },
                AutoTaggingSpecificationSchema.serializer(),
                fetchedAtMillis = now(),
            )
            ArrResult.ok(cachedAutoTaggingSchema())
        }

    /** Fetches one update log file's plain-text contents on demand; never cached. */
    suspend fun updateLogFileContents(filename: String): ArrResult<String> =
        withContext(ioContext) {
            try {
                ArrResult.ok(updateLogFileApi.updateLogFileContents(filename).string())
            } catch (e: IOException) {
                ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                ArrResult.err(e.arrErrorOrUnknown())
            }
        }

    // ---------------------------------------------------------------- writes

    /**
     * Saves the host config. The payload is stripped of secrets before it
     * is cached or queued: an offline PUT omits the secret fields, which
     * the server reads as unchanged, and nothing sensitive lands in the
     * entity store or action queue.
     */
    suspend fun saveHostConfig(
        config: HostConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val response = try {
            hostConfigApi.updateHostConfig(config.id.toString(), config)
        } catch (e: IOException) {
            return@withContext enqueueWrite(
                method = PUT_METHOD,
                path = "api/v3/config/host/${config.id}",
                body = config.strippedOfSecrets(),
                serializer = HostConfigResource.serializer(),
                cache = {
                    putConfigPreservingStamp(config.strippedOfSecrets())
                },
                description = "Save general settings",
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        applyWriteResponse(response) { saved ->
            putFreshRow(
                HOST_CONFIG_TYPE,
                SINGLE_ID,
                saved.strippedOfSecrets(),
                HostConfigResource.serializer(),
            )
        }
    }

    /** Triggers Radarr's Backup command; offline it queues for replay. */
    suspend fun createBackup(): ArrResult<WriteOutcome> = withContext(ioContext) {
        dispatchCommand(BACKUP_COMMAND, "Create backup")
    }

    /** Triggers Radarr's ApplicationUpdate command; offline it queues for replay. */
    suspend fun installUpdate(): ArrResult<WriteOutcome> = withContext(ioContext) {
        dispatchCommand(UPDATE_COMMAND, "Install Radarr update")
    }

    /** Restores a backup by id. 400 responses surface as [ArrError.Http]. */
    suspend fun restoreBackup(id: Int): ArrResult<BackupRestoreResource> =
        withContext(ioContext) {
            val response = try {
                backupApi.restoreBackup(id)
            } catch (e: IOException) {
                return@withContext ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            if (response.isSuccessful) {
                ArrResult.ok(response.body() ?: BackupRestoreResource())
            } else {
                ArrResult.err(response.toArrError())
            }
        }

    /** Deletes a backup; offline the removal queues and the row drops optimistically. */
    suspend fun deleteBackup(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        val response = try {
            backupApi.removeBackup(id)
        } catch (e: IOException) {
            return@withContext enqueueWrite(
                method = DELETE_METHOD,
                path = "api/v3/system/backup/$id",
                body = null,
                serializer = Unit.serializer(),
                cache = { store.delete(serverId, BACKUP_TYPE, id.toString()) },
                description = "Delete backup",
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        if (response.isSuccessful) {
            store.delete(serverId, BACKUP_TYPE, id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    /** Creates or updates a tag; `id <= 0` creates. */
    suspend fun saveTag(tag: TagResource): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = tag.id <= 0
        val response = try {
            if (isNew) tagApi.createTag(tag) else tagApi.updateTag(tag.id.toString(), tag)
        } catch (e: IOException) {
            return@withContext if (isNew) {
                enqueueOptimisticCreate(
                    path = "api/v3/tag",
                    body = tag,
                    serializer = TagResource.serializer(),
                    type = TAG_TYPE,
                    description = "Create tag",
                    error = ArrError.Network(e),
                )
            } else {
                enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/tag/${tag.id}",
                    body = tag,
                    serializer = TagResource.serializer(),
                    cache = { putRowPreservingStamp(TAG_TYPE, tag.id.toString(), tag, TagResource.serializer()) },
                    description = "Rename tag",
                    error = ArrError.Network(e),
                )
            }
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        applyWriteResponse(response) { saved ->
            putFreshRow(TAG_TYPE, saved.id.toString(), saved, TagResource.serializer())
        }
    }

    /** Deletes a tag; offline the removal queues and the row drops optimistically. */
    suspend fun deleteTag(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        val response = try {
            tagApi.removeTag(id)
        } catch (e: IOException) {
            return@withContext enqueueWrite(
                method = DELETE_METHOD,
                path = "api/v3/tag/$id",
                body = null,
                serializer = Unit.serializer(),
                cache = {
                    store.delete(serverId, TAG_TYPE, id.toString())
                    store.delete(serverId, TAG_DETAILS_TYPE, id.toString())
                },
                description = "Delete tag",
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        if (response.isSuccessful) {
            store.delete(serverId, TAG_TYPE, id.toString())
            store.delete(serverId, TAG_DETAILS_TYPE, id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    /** Creates or updates an auto tagging rule; `id <= 0` creates. */
    suspend fun saveAutoTagging(
        rule: AutoTaggingResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = rule.id <= 0
        val response = try {
            if (isNew) autoTaggingApi.createAutoTagging(rule) else autoTaggingApi.updateAutoTagging(rule.id.toString(), rule)
        } catch (e: IOException) {
            return@withContext if (isNew) {
                enqueueOptimisticCreate(
                    path = "api/v3/autotagging",
                    body = rule,
                    serializer = AutoTaggingResource.serializer(),
                    type = AUTO_TAGGING_TYPE,
                    description = "Create auto tagging rule",
                    error = ArrError.Network(e),
                )
            } else {
                enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/autotagging/${rule.id}",
                    body = rule,
                    serializer = AutoTaggingResource.serializer(),
                    cache = {
                        putRowPreservingStamp(
                            AUTO_TAGGING_TYPE,
                            rule.id.toString(),
                            rule,
                            AutoTaggingResource.serializer(),
                        )
                    },
                    description = "Save auto tagging rule",
                    error = ArrError.Network(e),
                )
            }
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        applyWriteResponse(response) { saved ->
            putFreshRow(
                AUTO_TAGGING_TYPE,
                saved.id.toString(),
                saved,
                AutoTaggingResource.serializer(),
            )
        }
    }

    /** Deletes an auto tagging rule; offline the removal queues optimistically. */
    suspend fun deleteAutoTagging(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        val response = try {
            autoTaggingApi.removeAutoTagging(id)
        } catch (e: IOException) {
            return@withContext enqueueWrite(
                method = DELETE_METHOD,
                path = "api/v3/autotagging/$id",
                body = null,
                serializer = Unit.serializer(),
                cache = { store.delete(serverId, AUTO_TAGGING_TYPE, id.toString()) },
                description = "Delete auto tagging rule",
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        if (response.isSuccessful) {
            store.delete(serverId, AUTO_TAGGING_TYPE, id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    // ----------------------------------------------------------------- queue

    /** Queued action count for this group's writes, for the pending banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { action ->
            action.serverId == serverId && (
                FEATURE_PATHS.any { action.path.startsWith(it) } ||
                    (action.path == COMMAND_PATH && action.description in COMMAND_DESCRIPTIONS)
                )
        }
    }

    /** Tag ids with a queued removal, for per-row queued badges. */
    fun queuedRemovedTagIds(): Flow<Set<Int>> = queuedRemovedIds(TAG_PATH)

    /** Auto tagging ids with a queued removal, for per-row queued badges. */
    fun queuedRemovedAutoTaggingIds(): Flow<Set<Int>> = queuedRemovedIds(AUTO_TAGGING_PATH)

    private fun queuedRemovedIds(pathPrefix: String): Flow<Set<Int>> =
        actionQueue.observe().map { pending ->
            pending.asSequence()
                .filter {
                    it.serverId == serverId && it.method == DELETE_METHOD && it.path.startsWith("$pathPrefix/")
                }
                .mapNotNull { it.path.removePrefix("$pathPrefix/").substringBefore('/').toIntOrNull() }
                .toSet()
        }

    // -------------------------------------------------------------- internals

    private suspend fun dispatchCommand(name: String, description: String): ArrResult<WriteOutcome> {
        val body = CommandResource(name = name)
        val response = try {
            commandApi.startCommand(body)
        } catch (e: IOException) {
            return enqueueWrite(
                method = POST_METHOD,
                path = COMMAND_PATH,
                body = body,
                serializer = CommandResource.serializer(),
                cache = {},
                description = description,
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            return ArrResult.err(e.arrErrorOrUnknown())
        }
        return if (response.isSuccessful) {
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    private suspend fun <T> enqueueOptimisticCreate(
        path: String,
        body: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        type: EntityType,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val tempId = tempIds.decrementAndGet().toInt()
        @Suppress("UNCHECKED_CAST")
        val optimistic = when (body) {
            is TagResource -> body.copy(id = tempId)
            is AutoTaggingResource -> body.copy(id = tempId)
            else -> body
        } as T
        val action = action(
            method = POST_METHOD,
            path = path,
            bodyJson = ArrJson.encodeToString(serializer, body),
            description = description,
        )
        putFreshRow(type, tempId.toString(), optimistic, serializer)
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private suspend fun <T> enqueueWrite(
        method: String,
        path: String,
        body: T?,
        serializer: kotlinx.serialization.KSerializer<T>,
        cache: suspend () -> Unit,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val bodyJson = body?.let { ArrJson.encodeToString(serializer, it) }
        val action = action(method, path, bodyJson, description)
        actionQueue.enqueue(action)
        cache()
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private fun action(method: String, path: String, bodyJson: String?, description: String) =
        QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.RADARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )

    private suspend fun <T> applyWriteResponse(
        response: Response<T>,
        onSuccessCache: suspend (T) -> Unit,
    ): ArrResult<WriteOutcome> = if (response.isSuccessful) {
        val body = response.body()
        if (body != null) onSuccessCache(body)
        ArrResult.ok(WriteOutcome.Synced)
    } else {
        ArrResult.err(response.toArrError())
    }

    /** Rewrites a type's cache to exactly the given rows, dropping rows the
     *  server no longer returns so optimistic edits and temp creates reconcile. */
    private suspend fun <T> replace(
        type: EntityType,
        values: List<T>,
        serializer: kotlinx.serialization.KSerializer<T>,
        stampMillis: Long,
    ) {
        val ids = values.map { entityIdOf(it) }
        store.getAll(serverId, type, serializer)
            .map { entityIdOf(it) }
            .filter { it !in ids }
            .forEach { store.delete(serverId, type, it) }
        if (values.isNotEmpty()) {
            store.putAll(
                serverId,
                type,
                values.map { entityIdOf(it) to it },
                serializer,
                fetchedAtMillis = stampMillis,
            )
        }
    }

    private suspend fun putConfigPreservingStamp(value: HostConfigResource) {
        val stamp = stampPreserving(HOST_CONFIG_TYPE)
        store.putAll(
            serverId,
            HOST_CONFIG_TYPE,
            listOf(SINGLE_ID to value),
            HostConfigResource.serializer(),
            fetchedAtMillis = stamp,
        )
    }

    /** Writes one row with a fresh stamp so TTL checks read this repo's clock. */
    private suspend fun <T> putFreshRow(
        type: EntityType,
        id: String,
        value: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        store.putAll(serverId, type, listOf(id to value), serializer, fetchedAtMillis = now())
    }

    private suspend fun <T> putRowPreservingStamp(
        type: EntityType,
        id: String,
        value: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        val stamp = stampPreserving(type)
        store.putAll(serverId, type, listOf(id to value), serializer, fetchedAtMillis = stamp)
    }

    private suspend fun stampPreserving(type: EntityType): Long =
        store.lastFetchedAt(serverId, type) ?: now()

    private fun entityIdOf(value: Any?): String = when (value) {
        is HostConfigResource -> SINGLE_ID
        is BackupResource -> value.id.toString()
        is UpdateResource -> value.id.toString()
        is SystemResource -> SINGLE_ID
        is TaskResource -> value.id.toString()
        is DiskSpaceResource -> value.id.toString()
        is HealthResource -> value.id.toString()
        is TagResource -> value.id.toString()
        is TagDetailsResource -> value.id.toString()
        is AutoTaggingResource -> value.id.toString()
        is AutoTaggingSpecificationSchema -> value.implementation ?: value.name ?: value.id.toString()
        is LogFileResource -> value.filename ?: value.id.toString()
        else -> value.hashCode().toString()
    }

    private suspend fun isTypeStale(type: EntityType, ttlMillis: Long): Boolean {
        val fetched = store.lastFetchedAt(serverId, type) ?: return true
        return now() - fetched > ttlMillis
    }

    private suspend fun <T> fetch(call: suspend () -> T): ArrResult<T> = try {
        ArrResult.ok(call())
    } catch (e: IOException) {
        ArrResult.err(ArrError.Network(e))
    } catch (e: Throwable) {
        ArrResult.err(e.arrErrorOrUnknown())
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), null)
    }

    private fun Throwable.arrErrorOrUnknown(): ArrError = when (this) {
        is retrofit2.HttpException -> when (code()) {
            401, 403 -> ArrError.Auth(code())
            else -> ArrError.Http(code(), message())
        }

        else -> ArrError.Unknown(this)
    }

    companion object {
        /**
         * Same cache rows the connect group writes, so both groups observe
         * one tag table per server. Disk space likewise shares the media
         * management group's rows.
         */
        internal val HOST_CONFIG_TYPE = EntityType("radarr.hostconfig")
        internal val BACKUP_TYPE = EntityType("radarr.backup")
        internal val UPDATE_TYPE = EntityType("radarr.update")
        internal val SYSTEM_STATUS_TYPE = EntityType("radarr.system.status")
        internal val TASK_TYPE = EntityType("radarr.task")
        internal val DISK_SPACE_TYPE = EntityType("radarr.diskspace")
        internal val HEALTH_TYPE = EntityType("radarr.health")
        internal val TAG_TYPE = EntityType("radarr.tag")
        internal val TAG_DETAILS_TYPE = EntityType("radarr.tagdetails")
        internal val AUTO_TAGGING_TYPE = EntityType("radarr.autotagging")
        internal val AUTO_TAGGING_SCHEMA_TYPE = EntityType("radarr.autotagging.schema")
        internal val UPDATE_LOG_FILE_TYPE = EntityType("radarr.updatelogfile")

        internal const val SINGLE_ID = "config"

        /** Five minutes; settings screens also re-read on pull-to-refresh. */
        const val SYSTEM_TTL_MILLIS: Long = 5 * 60 * 1000

        /** A day; the condition schema changes only on server upgrades. */
        const val SCHEMA_TTL_MILLIS: Long = 24 * 60 * 60 * 1000

        /** Optimistic creates render under ids far below any server-assigned id. */
        internal const val TEMP_ID_BASE = -1_000_000_000L

        internal const val TAG_PATH = "api/v3/tag"
        internal const val AUTO_TAGGING_PATH = "api/v3/autotagging"
        internal const val COMMAND_PATH = "api/v3/command"

        private const val PUT_METHOD = "PUT"
        private const val POST_METHOD = "POST"
        private const val DELETE_METHOD = "DELETE"

        /** Named commands this group dispatches. */
        const val BACKUP_COMMAND = "Backup"
        const val UPDATE_COMMAND = "ApplicationUpdate"

        private val FEATURE_PATHS = setOf(
            "api/v3/config/host",
            "api/v3/system/backup",
            TAG_PATH,
            AUTO_TAGGING_PATH,
        )

        /** Command-path actions are counted by description so other groups'
         *  command writes are not double-counted here. */
        private val COMMAND_DESCRIPTIONS = setOf("Create backup", "Install Radarr update")

        /** Removes secret-bearing fields so nothing sensitive is cached or queued. */
        internal fun HostConfigResource.strippedOfSecrets(): HostConfigResource = copy(
            password = null,
            passwordConfirmation = null,
            apiKey = null,
            sslCertPassword = null,
            oidcClientSecret = null,
            proxyPassword = null,
        )
    }
}
