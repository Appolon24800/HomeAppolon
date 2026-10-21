package dev.appolon.homelab.arr.feature.prowlarr.settings.data

import dev.appolon.homelab.arr.api.prowlarr.DevelopmentConfigApi
import dev.appolon.homelab.arr.api.prowlarr.DownloadClientApi
import dev.appolon.homelab.arr.api.prowlarr.HealthApi
import dev.appolon.homelab.arr.api.prowlarr.HostConfigApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerDefaultCategoriesApi
import dev.appolon.homelab.arr.api.prowlarr.LocalizationApi
import dev.appolon.homelab.arr.api.prowlarr.NotificationApi
import dev.appolon.homelab.arr.api.prowlarr.SystemApi
import dev.appolon.homelab.arr.api.prowlarr.TagApi
import dev.appolon.homelab.arr.api.prowlarr.TaskApi
import dev.appolon.homelab.arr.api.prowlarr.UiConfigApi
import dev.appolon.homelab.arr.api.prowlarr.model.DevelopmentConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientCategory
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import dev.appolon.homelab.arr.api.prowlarr.model.UiConfigResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.prowlarr.settings.ProwlarrSettingsFeatureApis
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

/** How a write landed: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * Cache-first repository for the Prowlarr settings group: download clients
 * with their grab-target categories, connect notifications with the event
 * matrix, the host, development, and UI config singletons, localization
 * options, and the system status, task, and health tables.
 *
 * Reads come from the [EntityStore] with fetchedAt TTLs per entity type.
 * Writes hit the server first; when the failure is retryable the change
 * applies optimistically to the cache and enqueues for replay through the
 * [ActionQueue]. Offline creates use a large negative temporary id so they
 * render immediately and reconcile away on the next refresh. Tests are
 * transient probes and never queue. Secret-bearing host config fields are
 * stripped before anything is cached or queued; a queued PUT omits them,
 * which the server reads as unchanged. Nothing throws; everything runs on
 * [ioContext].
 */
class ProwlarrSettingsRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val downloadClientApi: DownloadClientApi,
    private val notificationApi: NotificationApi,
    private val hostConfigApi: HostConfigApi,
    private val developmentConfigApi: DevelopmentConfigApi,
    private val uiConfigApi: UiConfigApi,
    private val localizationApi: LocalizationApi,
    private val systemApi: SystemApi,
    private val taskApi: TaskApi,
    private val healthApi: HealthApi,
    private val tagApi: TagApi,
    private val categoriesApi: IndexerDefaultCategoriesApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    private val tempIds = AtomicLong(TEMP_ID_BASE)

    /** Bumped per enqueue so queued writes replay in submission order. */
    private val writeSequence = AtomicLong(0L)

    // -------------------------------------------------------------- observe

    fun observeDownloadClients(): Flow<List<DownloadClientResource>> =
        store.observe(serverId, DOWNLOAD_CLIENT_TYPE, DownloadClientResource.serializer())

    fun observeNotifications(): Flow<List<NotificationResource>> =
        store.observe(serverId, NOTIFICATION_TYPE, NotificationResource.serializer())

    fun observeHostConfig(): Flow<HostConfigResource?> =
        store.observe(serverId, HOST_CONFIG_TYPE, HostConfigResource.serializer())
            .map { it.firstOrNull() }

    fun observeDevelopmentConfig(): Flow<DevelopmentConfigResource?> =
        store.observe(serverId, DEVELOPMENT_CONFIG_TYPE, DevelopmentConfigResource.serializer())
            .map { it.firstOrNull() }

    fun observeUiConfig(): Flow<UiConfigResource?> =
        store.observe(serverId, UI_CONFIG_TYPE, UiConfigResource.serializer())
            .map { it.firstOrNull() }

    fun observeSystemStatus(): Flow<SystemResource?> =
        store.observe(serverId, SYSTEM_STATUS_TYPE, SystemResource.serializer())
            .map { it.firstOrNull() }

    fun observeTasks(): Flow<List<TaskResource>> =
        store.observe(serverId, TASK_TYPE, TaskResource.serializer())

    fun observeHealth(): Flow<List<HealthResource>> =
        store.observe(serverId, HEALTH_TYPE, HealthResource.serializer())

    fun observeTags(): Flow<List<TagResource>> =
        store.observe(serverId, TAG_TYPE, TagResource.serializer())

    // --------------------------------------------------------------- cached

    suspend fun cachedDownloadClients(): List<DownloadClientResource> =
        store.getAll(serverId, DOWNLOAD_CLIENT_TYPE, DownloadClientResource.serializer())

    suspend fun cachedNotifications(): List<NotificationResource> =
        store.getAll(serverId, NOTIFICATION_TYPE, NotificationResource.serializer())

    suspend fun cachedHostConfig(): HostConfigResource? =
        store.get(serverId, HOST_CONFIG_TYPE, SINGLE_ID, HostConfigResource.serializer())

    suspend fun cachedDevelopmentConfig(): DevelopmentConfigResource? =
        store.get(serverId, DEVELOPMENT_CONFIG_TYPE, SINGLE_ID, DevelopmentConfigResource.serializer())

    suspend fun cachedUiConfig(): UiConfigResource? =
        store.get(serverId, UI_CONFIG_TYPE, SINGLE_ID, UiConfigResource.serializer())

    suspend fun cachedSystemStatus(): SystemResource? =
        store.get(serverId, SYSTEM_STATUS_TYPE, SINGLE_ID, SystemResource.serializer())

    suspend fun cachedTasks(): List<TaskResource> =
        store.getAll(serverId, TASK_TYPE, TaskResource.serializer())

    suspend fun cachedHealth(): List<HealthResource> =
        store.getAll(serverId, HEALTH_TYPE, HealthResource.serializer())

    // -------------------------------------------------------------- refresh

    /** True when any provider table this group renders is past its TTL. */
    suspend fun isStale(ttlMillis: Long = SETTINGS_TTL_MILLIS): Boolean =
        listOf(DOWNLOAD_CLIENT_TYPE, NOTIFICATION_TYPE).any { isTypeStale(it, ttlMillis) }

    suspend fun isGeneralStale(ttlMillis: Long = SETTINGS_TTL_MILLIS): Boolean =
        listOf(HOST_CONFIG_TYPE, DEVELOPMENT_CONFIG_TYPE, UI_CONFIG_TYPE, LOCALIZATION_TYPE)
            .any { isTypeStale(it, ttlMillis) }

    suspend fun isSystemStale(ttlMillis: Long = SETTINGS_TTL_MILLIS): Boolean =
        listOf(SYSTEM_STATUS_TYPE, TASK_TYPE, HEALTH_TYPE).any { isTypeStale(it, ttlMillis) }

    /**
     * Re-fetches both provider tables. Each type reconciles: rows the server
     * no longer returns (including optimistic temporary ones) drop out of
     * the cache. Tags refresh alongside so pickers stay current.
     */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        val clients = when (val r = fetch { downloadClientApi.getDownloadClients() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val notifications = when (val r = fetch { notificationApi.getNotifications() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        replace(
            DOWNLOAD_CLIENT_TYPE,
            clients,
            DownloadClientResource.serializer(),
            { it.id.toString() },
            stamp,
        )
        replace(
            NOTIFICATION_TYPE,
            notifications,
            NotificationResource.serializer(),
            { it.id.toString() },
            stamp,
        )
        refreshTags()
    }

    suspend fun refreshIfStale(ttlMillis: Long = SETTINGS_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /** Re-fetches config and localization, preserving cached rows when a member fails. */
    suspend fun refreshGeneral(): ArrResult<Unit> = withContext(ioContext) {
        val host = when (val r = fetch { hostConfigApi.getHostConfig() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        putConfigRow(HOST_CONFIG_TYPE, host.strippedOfSecrets(), HostConfigResource.serializer())
        when (val r = fetch { developmentConfigApi.getDevelopmentConfig() }) {
            is ArrResult.Ok -> putConfigRow(
                DEVELOPMENT_CONFIG_TYPE,
                r.value,
                DevelopmentConfigResource.serializer(),
            )

            is ArrResult.Err -> return@withContext r
        }
        when (val r = fetch { uiConfigApi.getUiConfig() }) {
            is ArrResult.Ok -> putConfigRow(UI_CONFIG_TYPE, r.value, UiConfigResource.serializer())
            is ArrResult.Err -> return@withContext r
        }
        when (val r = fetch { localizationApi.getLocalizationOptions() }) {
            is ArrResult.Ok -> replace(
                LOCALIZATION_TYPE,
                r.value,
                LocalizationOption.serializer(),
                { it.value ?: it.name ?: "" },
                now(),
            )

            is ArrResult.Err -> return@withContext r
        }
        ArrResult.ok(Unit)
    }

    suspend fun refreshGeneralIfStale(ttlMillis: Long = SETTINGS_TTL_MILLIS): ArrResult<Unit> =
        if (isGeneralStale(ttlMillis)) refreshGeneral() else ArrResult.ok(Unit)

    /** Re-fetches status, tasks, and health as one system group. */
    suspend fun refreshSystem(): ArrResult<Unit> = withContext(ioContext) {
        val status = when (val r = fetch { systemApi.getSystemStatus() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val tasks = when (val r = fetch { taskApi.getTasks() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val health = when (val r = fetch { healthApi.getHealth() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        putConfigRow(SYSTEM_STATUS_TYPE, status, SystemResource.serializer())
        replace(
            TASK_TYPE,
            tasks.sortedBy { it.name },
            TaskResource.serializer(),
            { it.id.toString() },
            stamp,
        )
        replace(
            HEALTH_TYPE,
            health.sortedBy { it.id },
            HealthResource.serializer(),
            { it.id.toString() },
            stamp,
        )
        ArrResult.ok(Unit)
    }

    suspend fun refreshSystemIfStale(ttlMillis: Long = SETTINGS_TTL_MILLIS): ArrResult<Unit> =
        if (isSystemStale(ttlMillis)) refreshSystem() else ArrResult.ok(Unit)

    /** Tag list for pickers; cached with its own TTL, shared with the indexers group. */
    suspend fun tags(ttlMillis: Long = TAG_TTL_MILLIS): ArrResult<List<TagResource>> =
        withContext(ioContext) {
            if (!isTypeStale(TAG_TYPE, ttlMillis)) {
                return@withContext ArrResult.ok(
                    store.getAll(serverId, TAG_TYPE, TagResource.serializer()),
                )
            }
            val fetched = when (val r = fetch { tagApi.getTags() }) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            replace(
                TAG_TYPE,
                fetched.sortedBy { it.label },
                TagResource.serializer(),
                { it.id.toString() },
                now(),
            )
            ArrResult.ok(fetched)
        }

    private suspend fun cachedTags(): List<TagResource> =
        store.getAll(serverId, TAG_TYPE, TagResource.serializer())

    private suspend fun refreshTags(): ArrResult<Unit> {
        val fetched = when (val result = fetch { tagApi.getTags() }) {
            is ArrResult.Err -> return result
            is ArrResult.Ok -> result.value
        }
        replace(
            TAG_TYPE,
            fetched.sortedBy { it.label },
            TagResource.serializer(),
            { it.id.toString() },
            now(),
        )
        return ArrResult.ok(Unit)
    }

    /** Resolves tag labels to ids, creating tags the server does not have yet. */
    suspend fun ensureTagIds(labels: List<String>): ArrResult<List<Int>> = withContext(ioContext) {
        val wanted = labels.map(String::trim).filter(String::isNotEmpty).distinct()
        var existing = cachedTags()
        if (existing.isEmpty()) {
            // An editor opened straight to the form may never have loaded the
            // tag cache; seed it from the server so known labels are not
            // needlessly re-created.
            existing = try {
                tagApi.getTags()
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            if (existing.isNotEmpty()) {
                replace(
                    TAG_TYPE,
                    existing.sortedBy { it.label },
                    TagResource.serializer(),
                    { it.id.toString() },
                    now(),
                )
            }
        }
        val byLabel = existing.associateBy { it.label.orEmpty() }
        val ids = mutableListOf<Int>()
        val created = mutableListOf<TagResource>()
        for (label in wanted) {
            val found = byLabel[label]
            if (found != null) {
                ids += found.id
            } else {
                val tag = try {
                    tagApi.createTag(TagResource(label = label))
                } catch (e: HttpException) {
                    return@withContext ArrResult.err(e.toArrError())
                } catch (e: Throwable) {
                    return@withContext ArrResult.err(e.arrError())
                }
                created += tag
                ids += tag.id
            }
        }
        if (created.isNotEmpty()) {
            store.putAll(
                serverId,
                TAG_TYPE,
                created.map { it.id.toString() to it },
                TagResource.serializer(),
                fetchedAtMillis = store.lastFetchedAt(serverId, TAG_TYPE) ?: now(),
            )
        }
        ArrResult.ok(ids.toList())
    }

    // --------------------------------------------------------------- schemas

    /** Download client definitions for the add flow; cached for a day. */
    suspend fun downloadClientSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<DownloadClientResource>> = withContext(ioContext) {
        listWithTtl(
            DOWNLOAD_CLIENT_SCHEMA_TYPE,
            DownloadClientResource.serializer(),
            { it.implementation ?: "dc" },
            ttlMillis,
        ) { downloadClientApi.getDownloadClientSchemas() }
    }

    /** Notification definitions for the add flow; cached for a day. */
    suspend fun notificationSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<NotificationResource>> = withContext(ioContext) {
        listWithTtl(
            NOTIFICATION_SCHEMA_TYPE,
            NotificationResource.serializer(),
            { it.implementation ?: "n" },
            ttlMillis,
        ) { notificationApi.getNotificationSchemas() }
    }

    /**
     * Cached list with TTL for rarely changing reference data. Schemas have
     * id 0 on the wire, so the cache key is the implementation. A fetch
     * failure falls back to stale rows when any exist.
     */
    private suspend fun <T> listWithTtl(
        type: EntityType,
        serializer: kotlinx.serialization.KSerializer<T>,
        keyOf: (T) -> String,
        ttlMillis: Long,
        fetcher: suspend () -> List<T>,
    ): ArrResult<List<T>> {
        if (!isTypeStale(type, ttlMillis)) {
            return ArrResult.ok(store.getAll(serverId, type, serializer))
        }
        val fetched = when (val r = fetch { fetcher() }) {
            is ArrResult.Err -> r

            is ArrResult.Ok -> r
        }
        if (fetched is ArrResult.Ok) {
            store.deleteAll(serverId, type)
            store.putAll(
                serverId,
                type,
                fetched.value.mapIndexed { index, value -> "${keyOf(value)}$index" to value },
                serializer,
                fetchedAtMillis = now(),
            )
        }
        return fetched
    }

    // -------------------------------------------------------- download clients

    /**
     * Creates (id 0) or updates one download client, grab-target categories
     * included. Offline creates cache the draft under a temporary negative
     * id that the next refresh reconciles.
     */
    suspend fun saveDownloadClient(
        resource: DownloadClientResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    downloadClientApi.createDownloadClient(resource = resource)
                } else {
                    downloadClientApi.updateDownloadClient(
                        id = resource.id.toString(),
                        resource = resource,
                    )
                }
            },
            method = if (isNew) POST_METHOD else PUT_METHOD,
            path = if (isNew) DOWNLOAD_CLIENT_PATH else "$DOWNLOAD_CLIENT_PATH/${resource.id}",
            body = resource,
            serializer = DownloadClientResource.serializer(),
            cache = { saved ->
                store.put(
                    serverId,
                    DOWNLOAD_CLIENT_TYPE,
                    saved.id.toString(),
                    saved,
                    DownloadClientResource.serializer(),
                )
            },
            cacheQueued = {
                if (isNew) {
                    val tempId = tempIds.getAndDecrement().toInt()
                    store.put(
                        serverId,
                        DOWNLOAD_CLIENT_TYPE,
                        tempId.toString(),
                        resource.copy(id = tempId),
                        DownloadClientResource.serializer(),
                    )
                } else {
                    store.put(
                        serverId,
                        DOWNLOAD_CLIENT_TYPE,
                        resource.id.toString(),
                        resource,
                        DownloadClientResource.serializer(),
                    )
                }
            },
            description = "Save download client ${resource.name.orEmpty()}",
        )
    }

    /** Row enable toggle; reads the cached row first so it stays one write. */
    suspend fun setDownloadClientEnabled(id: Int, enabled: Boolean): ArrResult<WriteOutcome> {
        val current = store.get(
            serverId,
            DOWNLOAD_CLIENT_TYPE,
            id.toString(),
            DownloadClientResource.serializer(),
        ) ?: return ArrResult.err(ArrError.Http(404, null))
        return saveDownloadClient(current.copy(enable = enabled))
    }

    suspend fun removeDownloadClient(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { downloadClientApi.deleteDownloadClient(id) },
            path = "$DOWNLOAD_CLIENT_PATH/$id",
            optimistic = { store.delete(serverId, DOWNLOAD_CLIENT_TYPE, id.toString()) },
            description = "Remove download client $id",
        )
    }

    suspend fun testDownloadClient(
        resource: DownloadClientResource,
    ): ArrResult<Unit> = withContext(ioContext) {
        testThrough { downloadClientApi.testDownloadClient(resource = resource) }
    }

    suspend fun testAllDownloadClients(): ArrResult<Unit> = withContext(ioContext) {
        testThrough { downloadClientApi.testAllDownloadClients() }
    }

    // ------------------------------------------------------------ notifications

    /** Creates (id 0) or updates one notification, event matrix included. */
    suspend fun saveNotification(
        resource: NotificationResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    notificationApi.createNotification(resource = resource)
                } else {
                    notificationApi.updateNotification(id = resource.id.toString(), resource = resource)
                }
            },
            method = if (isNew) POST_METHOD else PUT_METHOD,
            path = if (isNew) NOTIFICATION_PATH else "$NOTIFICATION_PATH/${resource.id}",
            body = resource,
            serializer = NotificationResource.serializer(),
            cache = { saved ->
                store.put(
                    serverId,
                    NOTIFICATION_TYPE,
                    saved.id.toString(),
                    saved,
                    NotificationResource.serializer(),
                )
            },
            cacheQueued = {
                if (isNew) {
                    val tempId = tempIds.getAndDecrement().toInt()
                    store.put(
                        serverId,
                        NOTIFICATION_TYPE,
                        tempId.toString(),
                        resource.copy(id = tempId),
                        NotificationResource.serializer(),
                    )
                } else {
                    store.put(
                        serverId,
                        NOTIFICATION_TYPE,
                        resource.id.toString(),
                        resource,
                        NotificationResource.serializer(),
                    )
                }
            },
            description = "Save notification ${resource.name.orEmpty()}",
        )
    }

    suspend fun removeNotification(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { notificationApi.deleteNotification(id) },
            path = "$NOTIFICATION_PATH/$id",
            optimistic = { store.delete(serverId, NOTIFICATION_TYPE, id.toString()) },
            description = "Remove notification $id",
        )
    }

    suspend fun testNotification(resource: NotificationResource): ArrResult<Unit> =
        withContext(ioContext) {
            testThrough { notificationApi.testNotification(resource = resource) }
        }

    suspend fun testAllNotifications(): ArrResult<Unit> = withContext(ioContext) {
        testThrough { notificationApi.testAllNotifications() }
    }

    // ---------------------------------------------------------------- configs

    /**
     * Saves the host config. The payload is stripped of secrets before it is
     * cached or queued: an offline PUT omits the secret fields, which the
     * server reads as unchanged, and nothing sensitive lands in the entity
     * store or action queue.
     */
    suspend fun saveHostConfig(
        config: HostConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        writeThrough(
            call = { hostConfigApi.updateHostConfig(id = config.id.toString(), resource = config) },
            method = PUT_METHOD,
            path = "$HOST_CONFIG_PATH/${config.id}",
            body = config.strippedOfSecrets(),
            serializer = HostConfigResource.serializer(),
            cache = { saved ->
                putConfigRow(
                    HOST_CONFIG_TYPE,
                    saved.strippedOfSecrets(),
                    HostConfigResource.serializer(),
                )
            },
            cacheQueued = {
                putConfigRowPreservingStamp(
                    HOST_CONFIG_TYPE,
                    config.strippedOfSecrets(),
                    HostConfigResource.serializer(),
                )
            },
            description = "Save host settings",
        )
    }

    suspend fun saveDevelopmentConfig(
        config: DevelopmentConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        writeThrough(
            call = {
                developmentConfigApi.updateDevelopmentConfig(
                    id = config.id.toString(),
                    resource = config,
                )
            },
            method = PUT_METHOD,
            path = "$DEVELOPMENT_CONFIG_PATH/${config.id}",
            body = config,
            serializer = DevelopmentConfigResource.serializer(),
            cache = { saved ->
                putConfigRow(
                    DEVELOPMENT_CONFIG_TYPE,
                    saved,
                    DevelopmentConfigResource.serializer(),
                )
            },
            cacheQueued = {
                putConfigRowPreservingStamp(
                    DEVELOPMENT_CONFIG_TYPE,
                    config,
                    DevelopmentConfigResource.serializer(),
                )
            },
            description = "Save development settings",
        )
    }

    /** Saves the UI config, whose only editable member is the UI culture. */
    suspend fun saveUiConfig(config: UiConfigResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            writeThrough(
                call = { uiConfigApi.updateUiConfig(id = config.id.toString(), resource = config) },
                method = PUT_METHOD,
                path = "$UI_CONFIG_PATH/${config.id}",
                body = config,
                serializer = UiConfigResource.serializer(),
                cache = { saved -> putConfigRow(UI_CONFIG_TYPE, saved, UiConfigResource.serializer()) },
                cacheQueued = {
                    putConfigRowPreservingStamp(
                        UI_CONFIG_TYPE,
                        config,
                        UiConfigResource.serializer(),
                    )
                },
                description = "Save UI settings",
            )
        }

    /** Localization options for the UI culture picker; cached with the general group. */
    suspend fun localizationOptions(): ArrResult<List<LocalizationOption>> = withContext(ioContext) {
        if (!isTypeStale(LOCALIZATION_TYPE, SETTINGS_TTL_MILLIS)) {
            val cached = store.getAll(serverId, LOCALIZATION_TYPE, LocalizationOption.serializer())
            if (cached.isNotEmpty()) return@withContext ArrResult.ok(cached)
        }
        when (val r = fetch { localizationApi.getLocalizationOptions() }) {
            is ArrResult.Ok -> {
                replace(
                    LOCALIZATION_TYPE,
                    r.value,
                    LocalizationOption.serializer(),
                    { it.value ?: it.name ?: "" },
                    now(),
                )
                ArrResult.ok(r.value)
            }

            is ArrResult.Err -> r
        }
    }

    /**
     * Root newznab categories offered by the grab-target editor. The table
     * is shared with the indexers group, so whichever group runs first seeds
     * it. Failures leave cached rows intact and return an error to the editor.
     */
    suspend fun newznabCategories(
        ttlMillis: Long = CATEGORY_TTL_MILLIS,
    ): ArrResult<List<IndexerCategory>> = withContext(ioContext) {
        if (!isTypeStale(CATEGORY_TYPE, ttlMillis)) {
            return@withContext ArrResult.ok(
                store.getAll(serverId, CATEGORY_TYPE, IndexerCategory.serializer()),
            )
        }
        when (val r = fetch { categoriesApi.getCategories() }) {
            is ArrResult.Ok -> {
                replace(
                    CATEGORY_TYPE,
                    r.value,
                    IndexerCategory.serializer(),
                    { it.id.toString() },
                    now(),
                )
                ArrResult.ok(r.value)
            }

            is ArrResult.Err -> r
        }
    }

    // ------------------------------------------------------------------ queue

    /** Queued action count for this group's writes, for the pending banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId && FEATURE_PATHS.any { p -> it.path.startsWith(p) } }
    }

    /** Provider ids with a queued removal, for per-row queued badges. */
    fun queuedRemovedIds(pathPrefix: String): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.method == DELETE_METHOD && it.path.startsWith("$pathPrefix/") }
            .mapNotNull { it.path.removePrefix("$pathPrefix/").substringBefore('/').toIntOrNull() }
            .toSet()
    }

    // -------------------------------------------------------------- internals

    /**
     * Server-first write. Plain calls throw [HttpException] on 4xx/5xx, so
     * the saved value arrives as the return value and HTTP failures map to
     * [ArrError]; transport failures queue optimistically.
     */
    private suspend fun <T> writeThrough(
        call: suspend () -> T,
        method: String,
        path: String,
        body: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        cache: suspend (T) -> Unit,
        cacheQueued: suspend () -> Unit,
        description: String,
    ): ArrResult<WriteOutcome> {
        val saved: T
        try {
            saved = call()
        } catch (e: IOException) {
            return enqueueWrite(method, path, body, serializer, cacheQueued, description, e.arrError())
        } catch (e: HttpException) {
            // Plain suspend calls throw on 4xx/5xx instead of answering.
            return ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return ArrResult.err(e.arrError())
        }
        cache(saved)
        return ArrResult.ok(WriteOutcome.Synced)
    }

    private suspend fun deleteThrough(
        call: suspend () -> Response<Unit>,
        path: String,
        optimistic: suspend () -> Unit,
        description: String,
    ): ArrResult<WriteOutcome> {
        val response = try {
            call()
        } catch (e: IOException) {
            val error = e.arrError()
            if (!error.isRetryable()) return ArrResult.err(error)
            val action = actionOf(DELETE_METHOD, path, null, description)
            optimistic()
            actionQueue.enqueue(action)
            return ArrResult.ok(WriteOutcome.Queued(action.actionId))
        } catch (e: Throwable) {
            return ArrResult.err(e.arrError())
        }
        if (response.isSuccessful) {
            optimistic()
            return ArrResult.ok(WriteOutcome.Synced)
        }
        return ArrResult.err(response.toArrError())
    }

    /** Single-provider test: answers a verdict, never queues. */
    private suspend fun testThrough(call: suspend () -> Response<Unit>): ArrResult<Unit> {
        val response = try {
            call()
        } catch (e: IOException) {
            return ArrResult.err(e.arrError())
        } catch (e: Throwable) {
            return ArrResult.err(e.arrError())
        }
        return if (response.isSuccessful) ArrResult.ok(Unit) else ArrResult.err(response.toArrError())
    }

    private suspend fun <T> enqueueWrite(
        method: String,
        path: String,
        body: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        cache: suspend () -> Unit,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = actionOf(method, path, ArrJson.encodeToString(serializer, body), description)
        cache()
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private fun actionOf(method: String, path: String, bodyJson: String?, description: String) =
        QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$bodyJson".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.PROWLARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            // The queue orders by timestamp and breaks ties on the random
            // action id, so rapid queued writes get a strictly increasing
            // stamp to keep their replay order.
            enqueuedAtMillis = now() + writeSequence.getAndIncrement(),
        )

    /** Rewrites a type's cache to exactly the given rows, dropping rows the
     *  server no longer returns so optimistic edits and temp creates reconcile. */
    private suspend fun <T> replace(
        type: EntityType,
        values: List<T>,
        serializer: kotlinx.serialization.KSerializer<T>,
        idOf: (T) -> String,
        stampMillis: Long,
    ) {
        store.deleteAll(serverId, type)
        if (values.isNotEmpty()) {
            store.putAll(
                serverId,
                type,
                values.map { idOf(it) to it },
                serializer,
                fetchedAtMillis = stampMillis,
            )
        }
    }

    private suspend fun <T> putConfigRow(
        type: EntityType,
        value: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        store.deleteAll(serverId, type)
        store.putAll(
            serverId,
            type,
            listOf(SINGLE_ID to value),
            serializer,
            fetchedAtMillis = now(),
        )
    }

    private suspend fun <T> putConfigRowPreservingStamp(
        type: EntityType,
        value: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ) {
        val stamp = store.lastFetchedAt(serverId, type) ?: now()
        store.deleteAll(serverId, type)
        store.putAll(serverId, type, listOf(SINGLE_ID to value), serializer, fetchedAtMillis = stamp)
    }

    private suspend fun isTypeStale(type: EntityType, ttlMillis: Long): Boolean {
        val fetched = store.lastFetchedAt(serverId, type) ?: return true
        return now() - fetched > ttlMillis
    }

    private suspend fun <T> fetch(call: suspend () -> T): ArrResult<T> = try {
        ArrResult.ok(call())
    } catch (e: IOException) {
        ArrResult.err(e.arrError())
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    /** Retrofit responses carry their error body separately from OkHttp's. */
    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> {
            val snippet = runCatching { errorBody()?.string() }.getOrNull()
                ?.trim()
                ?.take(200)
                ?.takeIf { it.isNotEmpty() }
            ArrError.Http(code(), snippet)
        }
    }

    companion object {
        internal val DOWNLOAD_CLIENT_TYPE = EntityType("prowlarr.downloadclient")
        internal val DOWNLOAD_CLIENT_SCHEMA_TYPE = EntityType("prowlarr.downloadclient.schema")
        internal val NOTIFICATION_TYPE = EntityType("prowlarr.notification")
        internal val NOTIFICATION_SCHEMA_TYPE = EntityType("prowlarr.notification.schema")
        internal val HOST_CONFIG_TYPE = EntityType("prowlarr.hostconfig")
        internal val DEVELOPMENT_CONFIG_TYPE = EntityType("prowlarr.developmentconfig")
        internal val UI_CONFIG_TYPE = EntityType("prowlarr.uiconfig")
        internal val LOCALIZATION_TYPE = EntityType("prowlarr.localization")
        internal val SYSTEM_STATUS_TYPE = EntityType("prowlarr.system.status")
        internal val TASK_TYPE = EntityType("prowlarr.task")
        internal val HEALTH_TYPE = EntityType("prowlarr.health")

        /**
         * Same cache rows the indexers group writes, so both groups observe
         * one tag and category table per server.
         */
        internal val TAG_TYPE = EntityType("prowlarr.tag")
        internal val CATEGORY_TYPE = EntityType("prowlarr.indexer.category")

        internal const val SINGLE_ID = "config"

        /** One minute; settings screens also re-read on pull-to-refresh. */
        const val SETTINGS_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** A day; provider definitions change only on server upgrades. */
        const val SCHEMA_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** One hour, matching the indexers group's tag TTL. */
        const val TAG_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** A day, matching the indexers group's category TTL. */
        const val CATEGORY_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** Optimistic creates render under ids far below any server-assigned id. */
        internal const val TEMP_ID_BASE = -1_000_000_000L

        internal const val DOWNLOAD_CLIENT_PATH = "api/v1/downloadclient"
        internal const val NOTIFICATION_PATH = "api/v1/notification"
        internal const val HOST_CONFIG_PATH = "api/v1/config/host"
        internal const val DEVELOPMENT_CONFIG_PATH = "api/v1/config/development"
        internal const val UI_CONFIG_PATH = "api/v1/config/ui"

        private const val PUT_METHOD = "PUT"
        private const val POST_METHOD = "POST"
        private const val DELETE_METHOD = "DELETE"

        private val FEATURE_PATHS = listOf(
            DOWNLOAD_CLIENT_PATH,
            NOTIFICATION_PATH,
            HOST_CONFIG_PATH,
            DEVELOPMENT_CONFIG_PATH,
            UI_CONFIG_PATH,
        )

        /** Removes secret-bearing fields so nothing sensitive is cached or queued. */
        internal fun HostConfigResource.strippedOfSecrets(): HostConfigResource = copy(
            password = null,
            passwordConfirmation = null,
            apiKey = null,
            sslCertPassword = null,
            proxyPassword = null,
        )

        /** Builds a repository from the API bundle of a connected server. */
        operator fun invoke(
            serverId: ServerId,
            store: EntityStore,
            apis: ProwlarrSettingsFeatureApis,
            actionQueue: ActionQueue,
            now: () -> Long = System::currentTimeMillis,
            ioContext: CoroutineContext = Dispatchers.IO,
        ): ProwlarrSettingsRepository = ProwlarrSettingsRepository(
            serverId = serverId,
            store = store,
            downloadClientApi = apis.downloadClientApi,
            notificationApi = apis.notificationApi,
            hostConfigApi = apis.hostConfigApi,
            developmentConfigApi = apis.developmentConfigApi,
            uiConfigApi = apis.uiConfigApi,
            localizationApi = apis.localizationApi,
            systemApi = apis.systemApi,
            taskApi = apis.taskApi,
            healthApi = apis.healthApi,
            tagApi = apis.tagApi,
            categoriesApi = apis.categoriesApi,
            actionQueue = actionQueue,
            now = now,
            ioContext = ioContext,
        )
    }
}

/**
 * One grab-target mapping row of a download client: the client-side category
 * name releases land in, plus the newznab category ids routed there.
 */
data class GrabTarget(
    val clientCategory: String = "",
    val categories: List<Int> = emptyList(),
)

/** Pure conversions between the wire category rows and the editable form. */
object GrabTargets {

    fun of(resource: DownloadClientResource): List<GrabTarget> =
        resource.categories.orEmpty().map { row ->
            GrabTarget(
                clientCategory = row.clientCategory.orEmpty(),
                categories = row.categories.orEmpty(),
            )
        }

    /**
     * Writes the mappings back. Rows with a blank name drop out, duplicate
     * category ids keep their first mapping, and empty rows vanish so the
     * payload never carries dead entries.
     */
    fun applyOnto(resource: DownloadClientResource, targets: List<GrabTarget>):
        DownloadClientResource {
        val seen = mutableSetOf<Int>()
        val rows = targets
            .filter { it.clientCategory.isNotBlank() || it.categories.isNotEmpty() }
            .mapNotNull { target ->
                val categories = target.categories.filter { seen.add(it) }
                if (target.clientCategory.isBlank() && categories.isEmpty()) {
                    null
                } else {
                    DownloadClientCategory(
                        clientCategory = target.clientCategory.trim().ifBlank { null },
                        categories = categories,
                    )
                }
            }
        return resource.copy(categories = rows)
    }
}
