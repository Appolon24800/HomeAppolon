package dev.appolon.homelab.arr.feature.radarr.settings.connect.data

import dev.appolon.homelab.arr.api.radarr.CommandApi
import dev.appolon.homelab.arr.api.radarr.DownloadClientApi
import dev.appolon.homelab.arr.api.radarr.DownloadClientConfigApi
import dev.appolon.homelab.arr.api.radarr.ImportListApi
import dev.appolon.homelab.arr.api.radarr.ImportListConfigApi
import dev.appolon.homelab.arr.api.radarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.radarr.ImportListMoviesApi
import dev.appolon.homelab.arr.api.radarr.IndexerApi
import dev.appolon.homelab.arr.api.radarr.IndexerConfigApi
import dev.appolon.homelab.arr.api.radarr.IndexerFlagApi
import dev.appolon.homelab.arr.api.radarr.MetadataApi
import dev.appolon.homelab.arr.api.radarr.NotificationApi
import dev.appolon.homelab.arr.api.radarr.RemotePathMappingApi
import dev.appolon.homelab.arr.api.radarr.TagApi
import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
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
import kotlinx.serialization.KSerializer
import retrofit2.Response

/**
 * Cache-first repository for the Radarr connect settings group: indexers,
 * download clients, remote path mappings, import lists with exclusions,
 * connect notifications, and metadata consumers. Provider definitions come
 * from the schema endpoints and render through the generic [Field] model.
 *
 * Reads come from the [EntityStore] with fetchedAt TTLs per entity type.
 * Writes hit the server first; when the network is unreachable the change
 * applies optimistically to the cache and enqueues for replay through the
 * [ActionQueue]. Offline creates use a large negative temporary id so they
 * render immediately and reconcile away on the next refresh. Tests are
 * transient probes and never queue. Nothing throws; everything runs on
 * [ioContext].
 */
class RadarrConnectRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val indexerApi: IndexerApi,
    private val indexerConfigApi: IndexerConfigApi,
    private val indexerFlagApi: IndexerFlagApi,
    private val downloadClientApi: DownloadClientApi,
    private val downloadClientConfigApi: DownloadClientConfigApi,
    private val notificationApi: NotificationApi,
    private val remotePathMappingApi: RemotePathMappingApi,
    private val importListApi: ImportListApi,
    private val importListConfigApi: ImportListConfigApi,
    private val importListExclusionApi: ImportListExclusionApi,
    private val importListMoviesApi: ImportListMoviesApi,
    private val metadataApi: MetadataApi,
    private val commandApi: CommandApi,
    private val tagApi: TagApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    private val tempIds = AtomicLong(TEMP_ID_BASE)

    /** Bumped per enqueue so queued writes replay in submission order. */
    private val writeSequence = AtomicLong(0L)

    // ------------------------------------------------------------------ reads

    fun observeIndexers(): Flow<List<IndexerResource>> =
        store.observe(serverId, INDEXER_TYPE, IndexerResource.serializer())

    fun observeDownloadClients(): Flow<List<DownloadClientResource>> =
        store.observe(serverId, DOWNLOAD_CLIENT_TYPE, DownloadClientResource.serializer())

    fun observeRemotePathMappings(): Flow<List<RemotePathMappingResource>> =
        store.observe(serverId, REMOTE_PATH_MAPPING_TYPE, RemotePathMappingResource.serializer())

    fun observeImportLists(): Flow<List<ImportListResource>> =
        store.observe(serverId, IMPORT_LIST_TYPE, ImportListResource.serializer())

    fun observeExclusions(): Flow<List<ImportListExclusionResource>> =
        store.observe(serverId, EXCLUSION_TYPE, ImportListExclusionResource.serializer())

    fun observeNotifications(): Flow<List<NotificationResource>> =
        store.observe(serverId, NOTIFICATION_TYPE, NotificationResource.serializer())

    fun observeMetadata(): Flow<List<MetadataResource>> =
        store.observe(serverId, METADATA_TYPE, MetadataResource.serializer())

    fun observeTags(): Flow<List<TagResource>> =
        store.observe(serverId, TAG_TYPE, TagResource.serializer())

    suspend fun cachedIndexers(): List<IndexerResource> =
        store.getAll(serverId, INDEXER_TYPE, IndexerResource.serializer())

    suspend fun cachedDownloadClients(): List<DownloadClientResource> =
        store.getAll(serverId, DOWNLOAD_CLIENT_TYPE, DownloadClientResource.serializer())

    suspend fun cachedImportLists(): List<ImportListResource> =
        store.getAll(serverId, IMPORT_LIST_TYPE, ImportListResource.serializer())

    suspend fun cachedNotifications(): List<NotificationResource> =
        store.getAll(serverId, NOTIFICATION_TYPE, NotificationResource.serializer())

    /** True when any of this screen group's list caches is absent or past its TTL. */
    suspend fun isStale(ttlMillis: Long = CONNECT_TTL_MILLIS): Boolean =
        listOf(
            INDEXER_TYPE,
            DOWNLOAD_CLIENT_TYPE,
            REMOTE_PATH_MAPPING_TYPE,
            IMPORT_LIST_TYPE,
            EXCLUSION_TYPE,
            NOTIFICATION_TYPE,
            METADATA_TYPE,
        ).any { type -> isTypeStale(type, ttlMillis) }

    /**
     * Re-fetches every provider table this group renders. Each type
     * reconciles: rows the server no longer returns (including optimistic
     * temporary ones) drop out of the cache. Tag and config caches refresh
     * alongside so pickers and options cards stay current.
     */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        val indexers = when (val r = fetch { indexerApi.indexers() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val clients = when (val r = fetch { downloadClientApi.downloadClients() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val mappings = when (val r = fetch { remotePathMappingApi.remotePathMappings() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val lists = when (val r = fetch { importListApi.importLists() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val exclusions = when (val r = fetch { importListExclusionApi.exclusions() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val notifications = when (val r = fetch { notificationApi.notifications() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val metadata = when (val r = fetch { metadataApi.metadata() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        replace(INDEXER_TYPE, indexers, IndexerResource.serializer(), stamp)
        replace(DOWNLOAD_CLIENT_TYPE, clients, DownloadClientResource.serializer(), stamp)
        replace(REMOTE_PATH_MAPPING_TYPE, mappings, RemotePathMappingResource.serializer(), stamp)
        replace(IMPORT_LIST_TYPE, lists, ImportListResource.serializer(), stamp)
        replace(EXCLUSION_TYPE, exclusions, ImportListExclusionResource.serializer(), stamp)
        replace(NOTIFICATION_TYPE, notifications, NotificationResource.serializer(), stamp)
        replace(METADATA_TYPE, metadata, MetadataResource.serializer(), stamp)
        refreshTags()
        refreshConfigs()
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(ttlMillis: Long = CONNECT_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /** Tag list for pickers, cached with its own TTL. */
    suspend fun tags(ttlMillis: Long = TAG_TTL_MILLIS): ArrResult<List<TagResource>> =
        withContext(ioContext) {
            if (!isTypeStale(TAG_TYPE, ttlMillis)) {
                return@withContext ArrResult.ok(
                    store.getAll(serverId, TAG_TYPE, TagResource.serializer()),
                )
            }
            val fetched = when (val r = fetch { tagApi.tags() }) {
                is ArrResult.Err -> return@withContext ArrResult.ok(cachedTags())
                is ArrResult.Ok -> r.value
            }
            replace(TAG_TYPE, fetched.sortedBy { it.label }, TagResource.serializer(), now())
            ArrResult.ok(fetched)
        }

    private suspend fun cachedTags(): List<TagResource> =
        store.getAll(serverId, TAG_TYPE, TagResource.serializer())

    /** Resolves tag labels to ids, creating tags the server does not have yet. */
    suspend fun ensureTagIds(labels: List<String>): ArrResult<List<Int>> =
        withContext(ioContext) {
            val wanted = labels.map(String::trim).filter(String::isNotEmpty).distinct()
            var existing = cachedTags()
            if (existing.isEmpty()) {
                // An editor opened straight to the form may never have loaded
                // the tag cache; seed it from the server so known labels are
                // not needlessly re-created.
                existing = try {
                    tagApi.tags()
                } catch (e: Throwable) {
                    emptyList()
                }
                if (existing.isNotEmpty()) {
                    replace(TAG_TYPE, existing.sortedBy { it.label }, TagResource.serializer(), now())
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
                    val response = try {
                        tagApi.createTag(TagResource(label = label))
                    } catch (e: IOException) {
                        return@withContext ArrResult.err(ArrError.Network(e))
                    } catch (e: Throwable) {
                        return@withContext ArrResult.err(ArrError.Unknown(e))
                    }
                    if (!response.isSuccessful) {
                        return@withContext ArrResult.err(response.toArrError())
                    }
                    created += response.body() ?: continue
                    ids += created.last().id
                }
            }
            if (created.isNotEmpty()) {
                store.putAll(
                    serverId,
                    TAG_TYPE,
                    created.map { it.id.toString() to it },
                    TagResource.serializer(),
                    fetchedAtMillis = now(),
                )
            }
            ArrResult.ok(ids.toList())
        }

    private suspend fun refreshTags() {
        val fetched = try {
            tagApi.tags()
        } catch (e: Throwable) {
            return
        }
        replace(TAG_TYPE, fetched.sortedBy { it.label }, TagResource.serializer(), now())
    }

    // --------------------------------------------------------------- schemas

    /** Provider definitions for the add flows; cached for a day. */
    suspend fun indexerSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<IndexerResource>> = withContext(ioContext) {
        listWithTtl(INDEXER_SCHEMA_TYPE, IndexerResource.serializer(), ttlMillis) {
            indexerApi.indexerSchema()
        }
    }

    suspend fun downloadClientSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<DownloadClientResource>> = withContext(ioContext) {
        listWithTtl(DOWNLOAD_CLIENT_SCHEMA_TYPE, DownloadClientResource.serializer(), ttlMillis) {
            downloadClientApi.downloadClientSchema()
        }
    }

    suspend fun importListSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<ImportListResource>> = withContext(ioContext) {
        listWithTtl(IMPORT_LIST_SCHEMA_TYPE, ImportListResource.serializer(), ttlMillis) {
            importListApi.importListSchema()
        }
    }

    suspend fun notificationSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<NotificationResource>> = withContext(ioContext) {
        listWithTtl(NOTIFICATION_SCHEMA_TYPE, NotificationResource.serializer(), ttlMillis) {
            notificationApi.notificationSchema()
        }
    }

    suspend fun metadataSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<MetadataResource>> = withContext(ioContext) {
        listWithTtl(METADATA_SCHEMA_TYPE, MetadataResource.serializer(), ttlMillis) {
            metadataApi.metadataSchema()
        }
    }

    private suspend fun <T> listWithTtl(
        type: EntityType,
        serializer: KSerializer<T>,
        ttlMillis: Long,
        fetcher: suspend () -> List<T>,
    ): ArrResult<List<T>> {
        if (!isTypeStale(type, ttlMillis)) {
            return ArrResult.ok(store.getAll(serverId, type, serializer))
        }
        val fetched: ArrResult<List<T>> = when (val r = fetch { fetcher() }) {
            is ArrResult.Err -> {
                val cached = store.getAll(serverId, type, serializer)
                if (cached.isNotEmpty()) ArrResult.ok(cached) else r
            }

            is ArrResult.Ok -> r
        }
        if (fetched is ArrResult.Ok) {
            store.deleteAll(serverId, type)
            store.putAll(
                serverId,
                type,
                fetched.value.mapIndexed { index, value ->
                    schemaKey(index, value) to value
                },
                serializer,
                fetchedAtMillis = now(),
            )
        }
        return fetched
    }

    /** Schemas have id 0 on the wire, so the cache key is the implementation. */
    private fun schemaKey(index: Int, value: Any?): String = when (value) {
        is IndexerResource -> value.implementation ?: "idx$index"
        is DownloadClientResource -> value.implementation ?: "dc$index"
        is ImportListResource -> value.implementation ?: "il$index"
        is NotificationResource -> value.implementation ?: "n$index"
        is MetadataResource -> value.implementation ?: "m$index"
        else -> "schema$index"
    }

    // ---------------------------------------------------------------- configs

    suspend fun cachedIndexerConfig(): IndexerConfigResource? =
        store.get(serverId, INDEXER_CONFIG_TYPE, SINGLE_ID, IndexerConfigResource.serializer())

    suspend fun cachedDownloadClientConfig(): DownloadClientConfigResource? =
        store.get(
            serverId,
            DOWNLOAD_CLIENT_CONFIG_TYPE,
            SINGLE_ID,
            DownloadClientConfigResource.serializer(),
        )

    suspend fun cachedImportListConfig(): ImportListConfigResource? =
        store.get(serverId, IMPORT_LIST_CONFIG_TYPE, SINGLE_ID, ImportListConfigResource.serializer())

    private suspend fun refreshConfigs() {
        try {
            store.put(
                serverId,
                INDEXER_CONFIG_TYPE,
                SINGLE_ID,
                indexerConfigApi.indexerConfig(),
                IndexerConfigResource.serializer(),
            )
        } catch (_: Throwable) {
            // Options cards keep the cached row; never fails the refresh.
        }
        try {
            store.put(
                serverId,
                DOWNLOAD_CLIENT_CONFIG_TYPE,
                SINGLE_ID,
                downloadClientConfigApi.downloadClientConfig(),
                DownloadClientConfigResource.serializer(),
            )
        } catch (_: Throwable) {
        }
        try {
            store.put(
                serverId,
                IMPORT_LIST_CONFIG_TYPE,
                SINGLE_ID,
                importListConfigApi.importListConfig(),
                ImportListConfigResource.serializer(),
            )
        } catch (_: Throwable) {
        }
    }

    suspend fun saveIndexerConfig(
        config: IndexerConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        writeThrough(
            call = { indexerConfigApi.updateIndexerConfig(config.id.toString(), config) },
            method = PUT_METHOD,
            path = "$INDEXER_PATH/config/${config.id}",
            body = config,
            serializer = IndexerConfigResource.serializer(),
            cache = { store.put(serverId, INDEXER_CONFIG_TYPE, SINGLE_ID, it, IndexerConfigResource.serializer()) },
            cacheQueued = {
                store.put(
                    serverId,
                    INDEXER_CONFIG_TYPE,
                    SINGLE_ID,
                    config,
                    IndexerConfigResource.serializer(),
                )
            },
            description = "Save indexer options",
        )
    }

    suspend fun saveDownloadClientConfig(
        config: DownloadClientConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        writeThrough(
            call = { downloadClientConfigApi.updateDownloadClientConfig(config.id.toString(), config) },
            method = PUT_METHOD,
            path = "$DOWNLOAD_CLIENT_PATH/config/${config.id}",
            body = config,
            serializer = DownloadClientConfigResource.serializer(),
            cache = {
                store.put(
                    serverId,
                    DOWNLOAD_CLIENT_CONFIG_TYPE,
                    SINGLE_ID,
                    it,
                    DownloadClientConfigResource.serializer(),
                )
            },
            cacheQueued = {
                store.put(
                    serverId,
                    DOWNLOAD_CLIENT_CONFIG_TYPE,
                    SINGLE_ID,
                    config,
                    DownloadClientConfigResource.serializer(),
                )
            },
            description = "Save download client options",
        )
    }

    suspend fun saveImportListConfig(
        config: ImportListConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        writeThrough(
            call = { importListConfigApi.updateImportListConfig(config.id.toString(), config) },
            method = PUT_METHOD,
            path = "$IMPORT_LIST_PATH/config/${config.id}",
            body = config,
            serializer = ImportListConfigResource.serializer(),
            cache = {
                store.put(
                    serverId,
                    IMPORT_LIST_CONFIG_TYPE,
                    SINGLE_ID,
                    it,
                    ImportListConfigResource.serializer(),
                )
            },
            cacheQueued = {
                store.put(
                    serverId,
                    IMPORT_LIST_CONFIG_TYPE,
                    SINGLE_ID,
                    config,
                    ImportListConfigResource.serializer(),
                )
            },
            description = "Save import list options",
        )
    }

    /** Read-only flag table the indexer editor shows for preferred flags. */
    suspend fun indexerFlags(): ArrResult<List<IndexerFlagResource>> = withContext(ioContext) {
        fetch { indexerFlagApi.indexerFlags() }
    }

    // --------------------------------------------------------------- indexers

    /**
     * Creates (id 0) or updates one indexer. Offline creates cache the draft
     * under a temporary negative id that the next refresh reconciles.
     */
    suspend fun saveIndexer(resource: IndexerResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val isNew = resource.id <= 0
            writeThrough(
                call = {
                    if (isNew) {
                        indexerApi.createIndexer(body = resource)
                    } else {
                        indexerApi.updateIndexer(id = resource.id, body = resource)
                    }
                },
                method = if (isNew) POST_METHOD else PUT_METHOD,
                path = if (isNew) INDEXER_PATH else "$INDEXER_PATH/${resource.id}",
                body = resource,
                serializer = IndexerResource.serializer(),
                cache = { saved ->
                    store.put(serverId, INDEXER_TYPE, saved.id.toString(), saved, IndexerResource.serializer())
                },
                cacheQueued = {
                    if (isNew) {
                        val tempId = tempIds.getAndDecrement().toInt()
                        store.put(
                            serverId,
                            INDEXER_TYPE,
                            tempId.toString(),
                            resource.copy(id = tempId),
                            IndexerResource.serializer(),
                        )
                    } else {
                        store.put(
                            serverId,
                            INDEXER_TYPE,
                            resource.id.toString(),
                            resource,
                            IndexerResource.serializer(),
                        )
                    }
                },
                description = "Save indexer ${resource.name.orEmpty()}",
            )
        }

    suspend fun removeIndexer(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { indexerApi.removeIndexer(id) },
            path = "$INDEXER_PATH/$id",
            optimistic = { store.delete(serverId, INDEXER_TYPE, id.toString()) },
            description = "Remove indexer $id",
        )
    }

    suspend fun testIndexer(resource: IndexerResource): ArrResult<Unit> = withContext(ioContext) {
        testThrough { indexerApi.testIndexer(body = resource) }
    }

    suspend fun testAllIndexers(): ArrResult<List<ProviderTestAllResult>> = withContext(ioContext) {
        testAllThrough { indexerApi.testAllIndexers() }
    }

    // -------------------------------------------------------- download clients

    suspend fun saveDownloadClient(
        resource: DownloadClientResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    downloadClientApi.createDownloadClient(body = resource)
                } else {
                    downloadClientApi.updateDownloadClient(id = resource.id, body = resource)
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

    /** Toggle from the list row; reads the cached row first so it stays one write. */
    suspend fun setDownloadClientEnabled(id: Int, enabled: Boolean): ArrResult<WriteOutcome> {
        val current = store.get(serverId, DOWNLOAD_CLIENT_TYPE, id.toString(), DownloadClientResource.serializer())
            ?: return ArrResult.err(ArrError.Http(404, null))
        return saveDownloadClient(current.copy(enable = enabled))
    }

    suspend fun removeDownloadClient(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { downloadClientApi.removeDownloadClient(id) },
            path = "$DOWNLOAD_CLIENT_PATH/$id",
            optimistic = { store.delete(serverId, DOWNLOAD_CLIENT_TYPE, id.toString()) },
            description = "Remove download client $id",
        )
    }

    suspend fun testDownloadClient(
        resource: DownloadClientResource,
    ): ArrResult<Unit> = withContext(ioContext) {
        testThrough { downloadClientApi.testDownloadClient(body = resource) }
    }

    suspend fun testAllDownloadClients(): ArrResult<List<ProviderTestAllResult>> =
        withContext(ioContext) {
            testAllThrough { downloadClientApi.testAllDownloadClients() }
        }

    // ---------------------------------------------------- remote path mappings

    suspend fun saveRemotePathMapping(
        resource: RemotePathMappingResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    remotePathMappingApi.createRemotePathMapping(resource)
                } else {
                    remotePathMappingApi.updateRemotePathMapping(resource.id.toString(), resource)
                }
            },
            method = if (isNew) POST_METHOD else PUT_METHOD,
            path = if (isNew) REMOTE_PATH_MAPPING_PATH else "$REMOTE_PATH_MAPPING_PATH/${resource.id}",
            body = resource,
            serializer = RemotePathMappingResource.serializer(),
            cache = { saved ->
                store.put(
                    serverId,
                    REMOTE_PATH_MAPPING_TYPE,
                    saved.id.toString(),
                    saved,
                    RemotePathMappingResource.serializer(),
                )
            },
            cacheQueued = {
                if (isNew) {
                    val tempId = tempIds.getAndDecrement().toInt()
                    store.put(
                        serverId,
                        REMOTE_PATH_MAPPING_TYPE,
                        tempId.toString(),
                        resource.copy(id = tempId),
                        RemotePathMappingResource.serializer(),
                    )
                } else {
                    store.put(
                        serverId,
                        REMOTE_PATH_MAPPING_TYPE,
                        resource.id.toString(),
                        resource,
                        RemotePathMappingResource.serializer(),
                    )
                }
            },
            description = "Save remote path mapping ${resource.remotePath.orEmpty()}",
        )
    }

    suspend fun removeRemotePathMapping(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { remotePathMappingApi.removeRemotePathMapping(id) },
            path = "$REMOTE_PATH_MAPPING_PATH/$id",
            optimistic = { store.delete(serverId, REMOTE_PATH_MAPPING_TYPE, id.toString()) },
            description = "Remove remote path mapping $id",
        )
    }

    // ------------------------------------------------------------ import lists

    suspend fun saveImportList(resource: ImportListResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val isNew = resource.id <= 0
            writeThrough(
                call = {
                    if (isNew) {
                        importListApi.createImportList(body = resource)
                    } else {
                        importListApi.updateImportList(id = resource.id, body = resource)
                    }
                },
                method = if (isNew) POST_METHOD else PUT_METHOD,
                path = if (isNew) IMPORT_LIST_PATH else "$IMPORT_LIST_PATH/${resource.id}",
                body = resource,
                serializer = ImportListResource.serializer(),
                cache = { saved ->
                    store.put(
                        serverId,
                        IMPORT_LIST_TYPE,
                        saved.id.toString(),
                        saved,
                        ImportListResource.serializer(),
                    )
                },
                cacheQueued = {
                    if (isNew) {
                        val tempId = tempIds.getAndDecrement().toInt()
                        store.put(
                            serverId,
                            IMPORT_LIST_TYPE,
                            tempId.toString(),
                            resource.copy(id = tempId),
                            ImportListResource.serializer(),
                        )
                    } else {
                        store.put(
                            serverId,
                            IMPORT_LIST_TYPE,
                            resource.id.toString(),
                            resource,
                            ImportListResource.serializer(),
                        )
                    }
                },
                description = "Save import list ${resource.name.orEmpty()}",
            )
        }

    suspend fun removeImportList(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { importListApi.removeImportList(id) },
            path = "$IMPORT_LIST_PATH/$id",
            optimistic = { store.delete(serverId, IMPORT_LIST_TYPE, id.toString()) },
            description = "Remove import list $id",
        )
    }

    suspend fun testImportList(resource: ImportListResource): ArrResult<Unit> = withContext(ioContext) {
        testThrough { importListApi.testImportList(body = resource) }
    }

    suspend fun testAllImportLists(): ArrResult<List<ProviderTestAllResult>> =
        withContext(ioContext) {
            testAllThrough { importListApi.testAllImportLists() }
        }

    /**
     * Sync preview: everything the configured lists currently discover,
     * with the existing/excluded flags the server resolves. Cached so the
     * preview reopens offline.
     */
    suspend fun importListPreview(): ArrResult<List<ImportListMoviesResource>> =
        withContext(ioContext) {
            val fetched: ArrResult<List<ImportListMoviesResource>> =
                when (val r = fetch { importListMoviesApi.importListMovies() }) {
                    is ArrResult.Err -> {
                        val cached = store.getAll(
                            serverId,
                            IMPORT_LIST_PREVIEW_TYPE,
                            ImportListMoviesResource.serializer(),
                        )
                        if (cached.isNotEmpty()) ArrResult.ok(cached) else r
                    }

                    is ArrResult.Ok -> r
                }
            if (fetched is ArrResult.Ok) {
                replace(
                    IMPORT_LIST_PREVIEW_TYPE,
                    fetched.value,
                    ImportListMoviesResource.serializer(),
                    now(),
                )
            }
            fetched
        }

    /**
     * Triggers the server-side ImportListSync command. Commands are probes
     * like tests: they never queue, because replaying a sync later without
     * the user looking would surprise them.
     */
    suspend fun syncImportLists(): ArrResult<Unit> = withContext(ioContext) {
        val response = try {
            commandApi.startCommand(CommandResource(name = IMPORT_LIST_SYNC_COMMAND))
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(ArrError.Unknown(e))
        }
        if (response.isSuccessful) ArrResult.ok(Unit) else ArrResult.err(response.toArrError())
    }

    // -------------------------------------------------------------- exclusions

    suspend fun addExclusion(
        resource: ImportListExclusionResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    importListExclusionApi.createExclusion(resource)
                } else {
                    importListExclusionApi.updateExclusion(resource.id.toString(), resource)
                }
            },
            method = if (isNew) POST_METHOD else PUT_METHOD,
            path = if (isNew) EXCLUSION_PATH else "$EXCLUSION_PATH/${resource.id}",
            body = resource,
            serializer = ImportListExclusionResource.serializer(),
            cache = { saved ->
                store.put(
                    serverId,
                    EXCLUSION_TYPE,
                    saved.id.toString(),
                    saved,
                    ImportListExclusionResource.serializer(),
                )
            },
            cacheQueued = {
                if (isNew) {
                    val tempId = tempIds.getAndDecrement().toInt()
                    store.put(
                        serverId,
                        EXCLUSION_TYPE,
                        tempId.toString(),
                        resource.copy(id = tempId),
                        ImportListExclusionResource.serializer(),
                    )
                } else {
                    store.put(
                        serverId,
                        EXCLUSION_TYPE,
                        resource.id.toString(),
                        resource,
                        ImportListExclusionResource.serializer(),
                    )
                }
            },
            description = "Save exclusion ${resource.movieTitle.orEmpty()}",
        )
    }

    suspend fun removeExclusion(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { importListExclusionApi.removeExclusion(id) },
            path = "$EXCLUSION_PATH/$id",
            optimistic = { store.delete(serverId, EXCLUSION_TYPE, id.toString()) },
            description = "Remove exclusion $id",
        )
    }

    // ------------------------------------------------------------ notifications

    suspend fun saveNotification(
        resource: NotificationResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    notificationApi.createNotification(body = resource)
                } else {
                    notificationApi.updateNotification(id = resource.id, body = resource)
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
            call = { notificationApi.removeNotification(id) },
            path = "$NOTIFICATION_PATH/$id",
            optimistic = { store.delete(serverId, NOTIFICATION_TYPE, id.toString()) },
            description = "Remove notification $id",
        )
    }

    suspend fun testNotification(
        resource: NotificationResource,
    ): ArrResult<Unit> = withContext(ioContext) {
        testThrough { notificationApi.testNotification(body = resource) }
    }

    suspend fun testAllNotifications(): ArrResult<List<ProviderTestAllResult>> =
        withContext(ioContext) {
            testAllThrough { notificationApi.testAllNotifications() }
        }

    // ---------------------------------------------------------------- metadata

    suspend fun saveMetadata(resource: MetadataResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val isNew = resource.id <= 0
            writeThrough(
                call = {
                    if (isNew) {
                        metadataApi.createMetadata(body = resource)
                    } else {
                        metadataApi.updateMetadata(id = resource.id, body = resource)
                    }
                },
                method = if (isNew) POST_METHOD else PUT_METHOD,
                path = if (isNew) METADATA_PATH else "$METADATA_PATH/${resource.id}",
                body = resource,
                serializer = MetadataResource.serializer(),
                cache = { saved ->
                    store.put(
                        serverId,
                        METADATA_TYPE,
                        saved.id.toString(),
                        saved,
                        MetadataResource.serializer(),
                    )
                },
                cacheQueued = {
                    if (isNew) {
                        val tempId = tempIds.getAndDecrement().toInt()
                        store.put(
                            serverId,
                            METADATA_TYPE,
                            tempId.toString(),
                            resource.copy(id = tempId),
                            MetadataResource.serializer(),
                        )
                    } else {
                        store.put(
                            serverId,
                            METADATA_TYPE,
                            resource.id.toString(),
                            resource,
                            MetadataResource.serializer(),
                        )
                    }
                },
                description = "Save metadata consumer ${resource.name.orEmpty()}",
            )
        }

    suspend fun removeMetadata(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { metadataApi.removeMetadata(id) },
            path = "$METADATA_PATH/$id",
            optimistic = { store.delete(serverId, METADATA_TYPE, id.toString()) },
            description = "Remove metadata consumer $id",
        )
    }

    suspend fun testMetadata(resource: MetadataResource): ArrResult<Unit> = withContext(ioContext) {
        testThrough { metadataApi.testMetadata(body = resource) }
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

    // --------------------------------------------------------------- internals

    private suspend fun <T> writeThrough(
        call: suspend () -> Response<T>,
        method: String,
        path: String,
        body: T,
        serializer: KSerializer<T>,
        cache: suspend (T) -> Unit,
        cacheQueued: suspend () -> Unit,
        description: String,
    ): ArrResult<WriteOutcome> {
        val response = try {
            call()
        } catch (e: IOException) {
            return enqueueWrite(method, path, body, serializer, cacheQueued, description, ArrError.Network(e))
        } catch (e: Throwable) {
            return ArrResult.err(ArrError.Unknown(e))
        }
        return if (response.isSuccessful) {
            val bodySaved = response.body()
            if (bodySaved != null) cache(bodySaved)
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    private suspend fun <T> deleteThrough(
        call: suspend () -> T,
        path: String,
        optimistic: suspend () -> Unit,
        description: String,
    ): ArrResult<WriteOutcome> {
        try {
            when (val result = call()) {
                is Response<*> -> if (!result.isSuccessful) {
                    return ArrResult.err(result.toArrError())
                }
            }
        } catch (e: IOException) {
            val error = ArrError.Network(e)
            val action = QueuedAction(
                actionId = ActionId(UUID.randomUUID().toString()),
                serverId = serverId,
                appType = AppType.RADARR,
                method = DELETE_METHOD,
                path = path,
                description = description,
                enqueuedAtMillis = now() + writeSequence.getAndIncrement(),
            )
            optimistic()
            actionQueue.enqueue(action)
            return ArrResult.ok(WriteOutcome.Queued(action.actionId))
        } catch (e: Throwable) {
            return ArrResult.err(ArrError.Unknown(e))
        }
        optimistic()
        return ArrResult.ok(WriteOutcome.Synced)
    }

    private suspend fun <T> enqueueWrite(
        method: String,
        path: String,
        body: T,
        serializer: KSerializer<T>,
        cache: suspend () -> Unit,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.RADARR,
            method = method,
            path = path,
            bodyJson = ArrJson.encodeToString(serializer, body),
            description = description,
            // The queue orders by timestamp and breaks ties on the random
            // action id, so rapid queued writes get a strictly increasing
            // stamp to keep their replay order.
            enqueuedAtMillis = now() + writeSequence.getAndIncrement(),
        )
        cache()
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Single-provider test: answers a verdict, never queues. */
    private suspend fun testThrough(call: suspend () -> Response<Unit>): ArrResult<Unit> {
        val response = try {
            call()
        } catch (e: IOException) {
            return ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return ArrResult.err(ArrError.Unknown(e))
        }
        return if (response.isSuccessful) ArrResult.ok(Unit) else ArrResult.err(response.toArrError())
    }

    /** Test-all: answers per-provider verdicts, never queues. */
    private suspend fun testAllThrough(
        call: suspend () -> Response<List<ProviderTestAllResult>>,
    ): ArrResult<List<ProviderTestAllResult>> {
        val response = try {
            call()
        } catch (e: IOException) {
            return ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return ArrResult.err(ArrError.Unknown(e))
        }
        return if (response.isSuccessful) {
            ArrResult.ok(response.body().orEmpty())
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    private suspend fun <T> replace(
        type: EntityType,
        values: List<T>,
        serializer: KSerializer<T>,
        stamp: Long,
    ) {
        store.deleteAll(serverId, type)
        store.putAll(
            serverId,
            type,
            values.map { providerIdOf(it).toString() to it },
            serializer,
            fetchedAtMillis = stamp,
        )
    }

    /** Row ids per provider type; negative temporary ids keep their identity. */
    private fun providerIdOf(value: Any?): String = when (value) {
        is IndexerResource -> value.id.toString()
        is DownloadClientResource -> value.id.toString()
        is RemotePathMappingResource -> value.id.toString()
        is ImportListResource -> value.id.toString()
        is ImportListExclusionResource -> value.id.toString()
        is ImportListMoviesResource -> value.id.toString()
        is NotificationResource -> value.id.toString()
        is MetadataResource -> value.id.toString()
        is TagResource -> value.id.toString()
        else -> throw IllegalArgumentException("Unsupported provider row")
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
        ArrResult.err(ArrError.Unknown(e))
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), null)
    }

    companion object {
        internal val INDEXER_TYPE = EntityType("radarr.indexer")
        internal val INDEXER_SCHEMA_TYPE = EntityType("radarr.indexer.schema")
        internal val INDEXER_CONFIG_TYPE = EntityType("radarr.indexer.config")
        internal val DOWNLOAD_CLIENT_TYPE = EntityType("radarr.downloadclient")
        internal val DOWNLOAD_CLIENT_SCHEMA_TYPE = EntityType("radarr.downloadclient.schema")
        internal val DOWNLOAD_CLIENT_CONFIG_TYPE = EntityType("radarr.downloadclient.config")
        internal val REMOTE_PATH_MAPPING_TYPE = EntityType("radarr.remotepathmapping")
        internal val IMPORT_LIST_TYPE = EntityType("radarr.importlist")
        internal val IMPORT_LIST_SCHEMA_TYPE = EntityType("radarr.importlist.schema")
        internal val IMPORT_LIST_CONFIG_TYPE = EntityType("radarr.importlist.config")
        internal val IMPORT_LIST_PREVIEW_TYPE = EntityType("radarr.importlist.preview")
        internal val EXCLUSION_TYPE = EntityType("radarr.importlist.exclusion")
        internal val NOTIFICATION_TYPE = EntityType("radarr.notification")
        internal val NOTIFICATION_SCHEMA_TYPE = EntityType("radarr.notification.schema")
        internal val METADATA_TYPE = EntityType("radarr.metadata")
        internal val METADATA_SCHEMA_TYPE = EntityType("radarr.metadata.schema")
        internal val TAG_TYPE = EntityType("radarr.tag")

        internal const val SINGLE_ID = "1"

        /** One minute; settings screens also re-read on pull-to-refresh. */
        const val CONNECT_TTL_MILLIS: Long = 60 * 1000

        /** A day; provider definitions change only on server upgrades. */
        const val SCHEMA_TTL_MILLIS: Long = 24 * 60 * 60 * 1000

        /** Five minutes, matching the other settings groups' config rows. */
        const val TAG_TTL_MILLIS: Long = 5 * 60 * 1000

        internal const val IMPORT_LIST_SYNC_COMMAND = "ImportListSync"

        internal const val INDEXER_PATH = "api/v3/indexer"
        internal const val DOWNLOAD_CLIENT_PATH = "api/v3/downloadclient"
        internal const val REMOTE_PATH_MAPPING_PATH = "api/v3/remotepathmapping"
        internal const val IMPORT_LIST_PATH = "api/v3/importlist"
        internal const val EXCLUSION_PATH = "api/v3/exclusions"
        internal const val NOTIFICATION_PATH = "api/v3/notification"
        internal const val METADATA_PATH = "api/v3/metadata"
        private val FEATURE_PATHS = listOf(
            INDEXER_PATH,
            DOWNLOAD_CLIENT_PATH,
            REMOTE_PATH_MAPPING_PATH,
            IMPORT_LIST_PATH,
            EXCLUSION_PATH,
            NOTIFICATION_PATH,
            METADATA_PATH,
        )

        /** Large negative base so offline placeholder rows never collide. */
        internal const val TEMP_ID_BASE = -100_000L

        private const val POST_METHOD = "POST"
        private const val PUT_METHOD = "PUT"
        private const val DELETE_METHOD = "DELETE"
    }
}
