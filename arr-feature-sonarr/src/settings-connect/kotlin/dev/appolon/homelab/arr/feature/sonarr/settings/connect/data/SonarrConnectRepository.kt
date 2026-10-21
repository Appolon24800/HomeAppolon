package dev.appolon.homelab.arr.feature.sonarr.settings.connect.data

import dev.appolon.homelab.arr.api.sonarr.CommandApi
import dev.appolon.homelab.arr.api.sonarr.DownloadClientApi
import dev.appolon.homelab.arr.api.sonarr.DownloadClientConfigApi
import dev.appolon.homelab.arr.api.sonarr.ImportListApi
import dev.appolon.homelab.arr.api.sonarr.ImportListConfigApi
import dev.appolon.homelab.arr.api.sonarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.sonarr.IndexerApi
import dev.appolon.homelab.arr.api.sonarr.IndexerConfigApi
import dev.appolon.homelab.arr.api.sonarr.IndexerFlagApi
import dev.appolon.homelab.arr.api.sonarr.MetadataApi
import dev.appolon.homelab.arr.api.sonarr.NotificationApi
import dev.appolon.homelab.arr.api.sonarr.RemotePathMappingApi
import dev.appolon.homelab.arr.api.sonarr.TagApi
import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import dev.appolon.homelab.arr.api.sonarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.sonarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

/**
 * Cache-first repository for the Sonarr connect settings group: indexers,
 * download clients, remote path mappings, import lists with exclusions,
 * connect notifications, and metadata consumers. Provider definitions come
 * from the schema endpoints and render through the generic Field model.
 *
 * Reads come from the [EntityStore] with fetchedAt TTLs per entity type.
 * Writes hit the server first; when the network is unreachable the change
 * applies optimistically to the cache and enqueues for replay through the
 * [ActionQueue]. Offline creates use a large negative temporary id so they
 * render immediately and reconcile away on the next refresh. Tests are
 * transient probes and never queue. Nothing throws; everything runs on
 * [ioContext].
 *
 * Import list and exclusion rows share the entity types the add-series
 * group caches, so both screen groups read the same rows for one server.
 */
class SonarrConnectRepository(
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

    // ----------------------------------------------------------------- secrets

    /**
     * Real secret field values for this process only, never persisted. Two
     * key shapes: "action:&lt;actionId&gt;" holds the values captured when a
     * queued write was enqueued, "provider:&lt;path&gt;" holds the latest
     * known values per provider update path (from a refresh or a save), so a
     * re-save of a redacted cached row can restore them.
     */
    private val liveSecrets = ConcurrentHashMap<String, Map<String, JsonElement>>()

    /** True when the field spec marks the value secret (password, API key). */
    private fun isSecret(field: Field): Boolean =
        field.privacy == PrivacyLevel.PASSWORD || field.privacy == PrivacyLevel.APIKEY

    /** True when the field value is the redaction placeholder. */
    private fun isRedacted(field: Field): Boolean =
        (field.value as? JsonPrimitive)?.contentOrNull == SECRET_SENTINEL

    /** Real secret values keyed by field name; placeholders never qualify. */
    private fun secretValuesOf(fields: List<Field>?): Map<String, JsonElement> =
        fields.orEmpty()
            .filter { it.name != null && isSecret(it) && !isRedacted(it) }
            .filter { it.value != null && it.value !is JsonNull }
            .associate { it.name!! to it.value!! }

    /** Copies the field list with every secret value replaced by the sentinel. */
    private fun redactedFields(fields: List<Field>?): List<Field>? =
        fields?.map { field ->
            if (isSecret(field) && field.value != null && field.value !is JsonNull) {
                field.copy(value = JsonPrimitive(SECRET_SENTINEL))
            } else {
                field
            }
        }

    /** Secret field names still carrying the placeholder in this body. */
    private fun redactedSecretNames(fields: List<Field>?): Set<String> =
        fields.orEmpty()
            .filter { it.name != null && isSecret(it) && isRedacted(it) }
            .mapTo(mutableSetOf()) { it.name!! }

    private fun rememberProviderSecrets(path: String, fields: List<Field>?) {
        val secrets = secretValuesOf(fields)
        if (secrets.isNotEmpty()) liveSecrets["$PROVIDER_KEY_PREFIX$path"] = secrets
    }

    /**
     * Restores redacted secret values in a live body from [liveSecrets].
     * [rememberUnder] is the provider's update path for existing rows; null
     * for creates, which have nothing earlier to draw from. A body with a
     * placeholder that cannot be restored fails instead of saving, because
     * the placeholder must never reach the server.
     */
    private fun <T> resolveSecrets(
        body: T,
        rememberUnder: String?,
        fieldsOf: (T) -> List<Field>?,
        withFields: (T, List<Field>?) -> T,
    ): ArrResult<T> {
        val fields = fieldsOf(body).orEmpty()
        val redacted = redactedSecretNames(fields)
        if (redacted.isEmpty()) {
            rememberUnder?.let { rememberProviderSecrets(it, fields) }
            return ArrResult.ok(body)
        }
        val remembered = rememberUnder?.let { liveSecrets["$PROVIDER_KEY_PREFIX$it"] }.orEmpty()
        val unresolved = redacted - remembered.keys
        if (unresolved.isNotEmpty()) {
            return ArrResult.err(
                ArrError.Unknown(
                    IllegalStateException(
                        "Secret fields ${unresolved.sorted()} are redacted in the cached copy and " +
                            "were not re-entered; re-enter them and save again.",
                    ),
                ),
            )
        }
        return ArrResult.ok(
            withFields(
                body,
                fields.map { field ->
                    val name = field.name
                    if (name != null && isSecret(field) && isRedacted(field)) {
                        field.copy(value = remembered.getValue(name))
                    } else {
                        field
                    }
                },
            ),
        )
    }

    /**
     * Substitutes a queued body's placeholders from the values captured at
     * enqueue time, or null when they are gone (the process restarted). Null
     * means the action must fail rather than post placeholders.
     */
    private fun <T> resolveQueuedSecrets(
        action: QueuedAction,
        body: T,
        fieldsOf: (T) -> List<Field>?,
        withFields: (T, List<Field>?) -> T,
    ): T? {
        val fields = fieldsOf(body).orEmpty()
        val redacted = redactedSecretNames(fields)
        if (redacted.isEmpty()) return body
        val remembered = liveSecrets["$ACTION_KEY_PREFIX${action.actionId.value}"].orEmpty()
        if (!redacted.all(remembered::containsKey)) return null
        return withFields(
            body,
            fields.map { field ->
                val name = field.name
                if (name != null && isSecret(field) && isRedacted(field)) {
                    field.copy(value = remembered.getValue(name))
                } else {
                    field
                }
            },
        )
    }

    private fun sanitizeIndexer(r: IndexerResource) = r.copy(fields = redactedFields(r.fields))

    private fun sanitizeDownloadClient(r: DownloadClientResource) =
        r.copy(fields = redactedFields(r.fields))

    private fun sanitizeImportList(r: ImportListResource) = r.copy(fields = redactedFields(r.fields))

    private fun sanitizeNotification(r: NotificationResource) =
        r.copy(fields = redactedFields(r.fields))

    private fun sanitizeMetadata(r: MetadataResource) = r.copy(fields = redactedFields(r.fields))

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

    suspend fun cachedExclusions(): List<ImportListExclusionResource> =
        store.getAll(serverId, EXCLUSION_TYPE, ImportListExclusionResource.serializer())

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
        val indexers = when (val r = fetch { indexerApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val clients = when (val r = fetch { downloadClientApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val mappings = when (val r = fetch { remotePathMappingApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val lists = when (val r = fetch { importListApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val exclusions = when (val r = fetch { importListExclusionApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val notifications = when (val r = fetch { notificationApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val metadata = when (val r = fetch { metadataApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        // Fetched rows carry real secret values on the wire; remember them
        // for this session and cache only redacted copies.
        indexers.forEach { rememberProviderSecrets("$INDEXER_PATH/${it.id}", it.fields) }
        clients.forEach { rememberProviderSecrets("$DOWNLOAD_CLIENT_PATH/${it.id}", it.fields) }
        lists.forEach { rememberProviderSecrets("$IMPORT_LIST_PATH/${it.id}", it.fields) }
        notifications.forEach { rememberProviderSecrets("$NOTIFICATION_PATH/${it.id}", it.fields) }
        metadata.forEach { rememberProviderSecrets("$METADATA_PATH/${it.id}", it.fields) }
        replace(INDEXER_TYPE, indexers.map(::sanitizeIndexer), IndexerResource.serializer(), stamp)
        replace(DOWNLOAD_CLIENT_TYPE, clients.map(::sanitizeDownloadClient), DownloadClientResource.serializer(), stamp)
        replace(REMOTE_PATH_MAPPING_TYPE, mappings, RemotePathMappingResource.serializer(), stamp)
        replace(IMPORT_LIST_TYPE, lists.map(::sanitizeImportList), ImportListResource.serializer(), stamp)
        replace(EXCLUSION_TYPE, exclusions, ImportListExclusionResource.serializer(), stamp)
        replace(NOTIFICATION_TYPE, notifications.map(::sanitizeNotification), NotificationResource.serializer(), stamp)
        replace(METADATA_TYPE, metadata.map(::sanitizeMetadata), MetadataResource.serializer(), stamp)
        when (val result = refreshTags()) {
            is ArrResult.Err -> return@withContext result
            is ArrResult.Ok -> Unit
        }
        when (val result = refreshConfigs()) {
            is ArrResult.Err -> return@withContext result
            is ArrResult.Ok -> Unit
        }
        // A successful refresh proves this server reachable, so this
        // group's queued provider writes replay right here, restoring their
        // redacted secrets from the live vault, instead of waiting for the
        // sync screen's generic executor that cannot.
        replayQueuedActions()
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
            val fetched = when (val r = fetch { tagApi.list() }) {
                is ArrResult.Err -> return@withContext r
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
            val existing = cachedTags()
            val byLabel = existing.associateBy { it.label.orEmpty() }
            val ids = mutableListOf<Int>()
            val created = mutableListOf<TagResource>()
            for (label in wanted) {
                val found = byLabel[label]
                if (found != null) {
                    ids += found.id
                } else {
                    val response = try {
                        tagApi.create(TagResource(label = label))
                    } catch (e: IOException) {
                        return@withContext ArrResult.err(ArrError.Network(e))
                    } catch (e: HttpException) {
                        return@withContext ArrResult.err(e.toArrError())
                    } catch (e: Throwable) {
                        return@withContext ArrResult.err(ArrError.Unknown(e))
                    }
                    created += response
                    ids += response.id
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

    private suspend fun refreshTags(): ArrResult<Unit> {
        val fetched = when (val result = fetch { tagApi.list() }) {
            is ArrResult.Err -> return result
            is ArrResult.Ok -> result.value
        }
        replace(TAG_TYPE, fetched.sortedBy { it.label }, TagResource.serializer(), now())
        return ArrResult.ok(Unit)
    }

    // --------------------------------------------------------------- schemas

    /** Provider definitions for the add flows; cached for a day. */
    suspend fun indexerSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<IndexerResource>> = withContext(ioContext) {
        listWithTtl(INDEXER_SCHEMA_TYPE, IndexerResource.serializer(), ttlMillis, ::sanitizeIndexer) {
            indexerApi.schema()
        }
    }

    suspend fun downloadClientSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<DownloadClientResource>> = withContext(ioContext) {
        listWithTtl(DOWNLOAD_CLIENT_SCHEMA_TYPE, DownloadClientResource.serializer(), ttlMillis, ::sanitizeDownloadClient) {
            downloadClientApi.schema()
        }
    }

    suspend fun importListSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<ImportListResource>> = withContext(ioContext) {
        listWithTtl(IMPORT_LIST_SCHEMA_TYPE, ImportListResource.serializer(), ttlMillis, ::sanitizeImportList) {
            importListApi.schema()
        }
    }

    suspend fun notificationSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<NotificationResource>> = withContext(ioContext) {
        listWithTtl(NOTIFICATION_SCHEMA_TYPE, NotificationResource.serializer(), ttlMillis, ::sanitizeNotification) {
            notificationApi.schema()
        }
    }

    suspend fun metadataSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<MetadataResource>> = withContext(ioContext) {
        listWithTtl(METADATA_SCHEMA_TYPE, MetadataResource.serializer(), ttlMillis, ::sanitizeMetadata) {
            metadataApi.schema()
        }
    }

    private suspend fun <T> listWithTtl(
        type: EntityType,
        serializer: kotlinx.serialization.KSerializer<T>,
        ttlMillis: Long,
        sanitize: (T) -> T = { it },
        fetcher: suspend () -> List<T>,
    ): ArrResult<List<T>> {
        if (!isTypeStale(type, ttlMillis)) {
            return ArrResult.ok(store.getAll(serverId, type, serializer))
        }
        val fetched: ArrResult<List<T>> = when (val r = fetch { fetcher() }) {
            is ArrResult.Err -> r

            is ArrResult.Ok -> r
        }
        if (fetched is ArrResult.Ok) {
            store.deleteAll(serverId, type)
            store.putAll(
                serverId,
                type,
                fetched.value.mapIndexed { index, value ->
                    schemaKey(index, value) to sanitize(value)
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

    private suspend fun refreshConfigs(): ArrResult<Unit> = fetch {
        store.put(
            serverId,
            INDEXER_CONFIG_TYPE,
            SINGLE_ID,
            indexerConfigApi.get(),
            IndexerConfigResource.serializer(),
        )
        store.put(
            serverId,
            DOWNLOAD_CLIENT_CONFIG_TYPE,
            SINGLE_ID,
            downloadClientConfigApi.get(),
            DownloadClientConfigResource.serializer(),
        )
        store.put(
            serverId,
            IMPORT_LIST_CONFIG_TYPE,
            SINGLE_ID,
            importListConfigApi.list(),
            ImportListConfigResource.serializer(),
        )
        Unit
    }

    suspend fun saveIndexerConfig(
        config: IndexerConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        writeThrough(
            call = { indexerConfigApi.update(config.id.toString(), config) },
            method = PUT_METHOD,
            path = "$INDEXER_CONFIG_PATH/${config.id}",
            body = config,
            serializer = IndexerConfigResource.serializer(),
            cache = { saved ->
                store.put(serverId, INDEXER_CONFIG_TYPE, SINGLE_ID, saved, IndexerConfigResource.serializer())
            },
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
            call = { downloadClientConfigApi.update(config.id.toString(), config) },
            method = PUT_METHOD,
            path = "$DOWNLOAD_CLIENT_CONFIG_PATH/${config.id}",
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
            call = { importListConfigApi.update(config.id.toString(), config) },
            method = PUT_METHOD,
            path = "$IMPORT_LIST_CONFIG_PATH/${config.id}",
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
        fetch { indexerFlagApi.list() }
    }

    // --------------------------------------------------------------- indexers

    /**
     * Creates (id 0) or updates one indexer. Offline creates cache the draft
     * under a temporary negative id that the next refresh reconciles.
     */
    suspend fun saveIndexer(resource: IndexerResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val isNew = resource.id <= 0
            val path = if (isNew) INDEXER_PATH else "$INDEXER_PATH/${resource.id}"
            val resolved = when (
                val r = resolveSecrets(
                    resource,
                    rememberUnder = if (isNew) null else path,
                    fieldsOf = { it.fields },
                    withFields = { res, fields -> res.copy(fields = fields) },
                )
            ) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            writeThrough(
                call = {
                    if (isNew) {
                        indexerApi.create(body = resolved)
                    } else {
                        indexerApi.update(id = resolved.id, body = resolved)
                    }
                },
                method = if (isNew) POST_METHOD else PUT_METHOD,
                path = path,
                body = resolved,
                serializer = IndexerResource.serializer(),
                cache = { saved ->
                    store.put(serverId, INDEXER_TYPE, saved.id.toString(), saved, IndexerResource.serializer())
                },
                cacheQueued = { redacted ->
                    putOptimistic(
                        INDEXER_TYPE,
                        isNew,
                        redacted,
                        IndexerResource.serializer(),
                        { it.id.toString() },
                    ) { r, id -> r.copy(id = id) }
                },
                description = "Save indexer ${resource.name.orEmpty()}",
                secretsOf = { secretValuesOf(it.fields) },
                sanitize = ::sanitizeIndexer,
            )
        }

    suspend fun removeIndexer(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { indexerApi.delete(id) },
            path = "$INDEXER_PATH/$id",
            optimistic = { store.delete(serverId, INDEXER_TYPE, id.toString()) },
            description = "Remove indexer $id",
        )
    }

    suspend fun testIndexer(resource: IndexerResource): ArrResult<Unit> = withContext(ioContext) {
        testThrough { indexerApi.test(body = resource) }
    }

    suspend fun testAllIndexers(): ArrResult<Unit> = withContext(ioContext) {
        testThrough { indexerApi.testAll() }
    }

    // -------------------------------------------------------- download clients

    suspend fun saveDownloadClient(
        resource: DownloadClientResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        val path = if (isNew) DOWNLOAD_CLIENT_PATH else "$DOWNLOAD_CLIENT_PATH/${resource.id}"
        val resolved = when (
            val r = resolveSecrets(
                resource,
                rememberUnder = if (isNew) null else path,
                fieldsOf = { it.fields },
                withFields = { res, fields -> res.copy(fields = fields) },
            )
        ) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        writeThrough(
            call = {
                if (isNew) {
                    downloadClientApi.create(body = resolved)
                } else {
                    downloadClientApi.update(id = resolved.id, body = resolved)
                }
            },
            method = if (isNew) POST_METHOD else PUT_METHOD,
            path = path,
            body = resolved,
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
            cacheQueued = { redacted ->
                putOptimistic(
                    DOWNLOAD_CLIENT_TYPE,
                    isNew,
                    redacted,
                    DownloadClientResource.serializer(),
                    { it.id.toString() },
                ) { r, id -> r.copy(id = id) }
            },
            description = "Save download client ${resource.name.orEmpty()}",
            secretsOf = { secretValuesOf(it.fields) },
            sanitize = ::sanitizeDownloadClient,
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
            call = { downloadClientApi.delete(id) },
            path = "$DOWNLOAD_CLIENT_PATH/$id",
            optimistic = { store.delete(serverId, DOWNLOAD_CLIENT_TYPE, id.toString()) },
            description = "Remove download client $id",
        )
    }

    suspend fun testDownloadClient(
        resource: DownloadClientResource,
    ): ArrResult<Unit> = withContext(ioContext) {
        testThrough { downloadClientApi.test(body = resource) }
    }

    suspend fun testAllDownloadClients(): ArrResult<Unit> = withContext(ioContext) {
        testThrough { downloadClientApi.testAll() }
    }

    // ---------------------------------------------------- remote path mappings

    suspend fun saveRemotePathMapping(
        resource: RemotePathMappingResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    remotePathMappingApi.create(resource)
                } else {
                    remotePathMappingApi.update(resource.id.toString(), resource)
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
                putOptimistic(
                    REMOTE_PATH_MAPPING_TYPE,
                    isNew,
                    resource,
                    RemotePathMappingResource.serializer(),
                    { it.id.toString() },
                ) { r, id -> r.copy(id = id) }
            },
            description = "Save remote path mapping ${resource.remotePath.orEmpty()}",
        )
    }

    suspend fun removeRemotePathMapping(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { remotePathMappingApi.delete(id) },
            path = "$REMOTE_PATH_MAPPING_PATH/$id",
            optimistic = { store.delete(serverId, REMOTE_PATH_MAPPING_TYPE, id.toString()) },
            description = "Remove remote path mapping $id",
        )
    }

    // ------------------------------------------------------------ import lists

    suspend fun saveImportList(resource: ImportListResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val isNew = resource.id <= 0
            val path = if (isNew) IMPORT_LIST_PATH else "$IMPORT_LIST_PATH/${resource.id}"
            val resolved = when (
                val r = resolveSecrets(
                    resource,
                    rememberUnder = if (isNew) null else path,
                    fieldsOf = { it.fields },
                    withFields = { res, fields -> res.copy(fields = fields) },
                )
            ) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            writeThrough(
                call = {
                    if (isNew) {
                        importListApi.create(body = resolved)
                    } else {
                        importListApi.update(id = resolved.id, body = resolved)
                    }
                },
                method = if (isNew) POST_METHOD else PUT_METHOD,
                path = path,
                body = resolved,
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
                cacheQueued = { redacted ->
                    putOptimistic(
                        IMPORT_LIST_TYPE,
                        isNew,
                        redacted,
                        ImportListResource.serializer(),
                        { it.id.toString() },
                    ) { r, id -> r.copy(id = id) }
                },
                description = "Save import list ${resource.name.orEmpty()}",
                secretsOf = { secretValuesOf(it.fields) },
                sanitize = ::sanitizeImportList,
            )
        }

    suspend fun removeImportList(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { importListApi.delete(id) },
            path = "$IMPORT_LIST_PATH/$id",
            optimistic = { store.delete(serverId, IMPORT_LIST_TYPE, id.toString()) },
            description = "Remove import list $id",
        )
    }

    suspend fun testImportList(resource: ImportListResource): ArrResult<Unit> =
        withContext(ioContext) {
            testThrough { importListApi.test(body = resource) }
        }

    suspend fun testAllImportLists(): ArrResult<Unit> = withContext(ioContext) {
        testThrough { importListApi.testAll() }
    }

    /**
     * Triggers the server-side ImportListSync command. Commands are probes
     * like tests: they never queue, because replaying a sync later without
     * the user looking would surprise them.
     */
    suspend fun syncImportLists(): ArrResult<Unit> = withContext(ioContext) {
        try {
            commandApi.create(CommandResource(name = IMPORT_LIST_SYNC_COMMAND))
            ArrResult.ok(Unit)
        } catch (e: IOException) {
            ArrResult.err(ArrError.Network(e))
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(ArrError.Unknown(e))
        }
    }

    // -------------------------------------------------------------- exclusions

    suspend fun addExclusion(
        resource: ImportListExclusionResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        writeThrough(
            call = {
                if (isNew) {
                    importListExclusionApi.create(resource)
                } else {
                    importListExclusionApi.update(resource.id.toString(), resource)
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
                putOptimistic(
                    EXCLUSION_TYPE,
                    isNew,
                    resource,
                    ImportListExclusionResource.serializer(),
                    { it.id.toString() },
                ) { r, id -> r.copy(id = id) }
            },
            description = "Save exclusion ${resource.title.orEmpty()}",
        )
    }

    /**
     * Removes an exclusion. A still-pending offline create is cancelled
     * instead of enqueueing a delete the replay would have to fight with.
     */
    suspend fun removeExclusion(resource: ImportListExclusionResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            if (resource.id <= 0) {
                cancelPendingCreate(
                    EXCLUSION_PATH,
                    ImportListExclusionResource.serializer(),
                ) { it.tvdbId == resource.tvdbId }
                store.delete(serverId, EXCLUSION_TYPE, resource.id.toString())
                return@withContext ArrResult.ok(WriteOutcome.Synced)
            }
            deleteThrough(
                call = { importListExclusionApi.delete(resource.id) },
                path = "$EXCLUSION_PATH/${resource.id}",
                optimistic = { store.delete(serverId, EXCLUSION_TYPE, resource.id.toString()) },
                description = "Remove exclusion ${resource.id}",
            )
        }

    // ------------------------------------------------------------ notifications

    suspend fun saveNotification(
        resource: NotificationResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = resource.id <= 0
        val path = if (isNew) NOTIFICATION_PATH else "$NOTIFICATION_PATH/${resource.id}"
        val resolved = when (
            val r = resolveSecrets(
                resource,
                rememberUnder = if (isNew) null else path,
                fieldsOf = { it.fields },
                withFields = { res, fields -> res.copy(fields = fields) },
            )
        ) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        writeThrough(
            call = {
                if (isNew) {
                    notificationApi.create(body = resolved)
                } else {
                    notificationApi.update(id = resolved.id, body = resolved)
                }
            },
            method = if (isNew) POST_METHOD else PUT_METHOD,
            path = path,
            body = resolved,
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
            cacheQueued = { redacted ->
                putOptimistic(
                    NOTIFICATION_TYPE,
                    isNew,
                    redacted,
                    NotificationResource.serializer(),
                    { it.id.toString() },
                ) { r, id -> r.copy(id = id) }
            },
            description = "Save notification ${resource.name.orEmpty()}",
            secretsOf = { secretValuesOf(it.fields) },
            sanitize = ::sanitizeNotification,
        )
    }

    suspend fun removeNotification(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { notificationApi.delete(id) },
            path = "$NOTIFICATION_PATH/$id",
            optimistic = { store.delete(serverId, NOTIFICATION_TYPE, id.toString()) },
            description = "Remove notification $id",
        )
    }

    suspend fun testNotification(
        resource: NotificationResource,
    ): ArrResult<Unit> = withContext(ioContext) {
        testThrough { notificationApi.test(body = resource) }
    }

    suspend fun testAllNotifications(): ArrResult<Unit> = withContext(ioContext) {
        testThrough { notificationApi.testAll() }
    }

    // ---------------------------------------------------------------- metadata

    suspend fun saveMetadata(resource: MetadataResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val isNew = resource.id <= 0
            val path = if (isNew) METADATA_PATH else "$METADATA_PATH/${resource.id}"
            val resolved = when (
                val r = resolveSecrets(
                    resource,
                    rememberUnder = if (isNew) null else path,
                    fieldsOf = { it.fields },
                    withFields = { res, fields -> res.copy(fields = fields) },
                )
            ) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            writeThrough(
                call = {
                    if (isNew) {
                        metadataApi.create(body = resolved)
                    } else {
                        metadataApi.update(id = resolved.id, body = resolved)
                    }
                },
                method = if (isNew) POST_METHOD else PUT_METHOD,
                path = path,
                body = resolved,
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
                cacheQueued = { redacted ->
                    putOptimistic(
                        METADATA_TYPE,
                        isNew,
                        redacted,
                        MetadataResource.serializer(),
                        { it.id.toString() },
                    ) { r, id -> r.copy(id = id) }
                },
                description = "Save metadata consumer ${resource.name.orEmpty()}",
                secretsOf = { secretValuesOf(it.fields) },
                sanitize = ::sanitizeMetadata,
            )
        }

    suspend fun setMetadataEnabled(id: Int, enabled: Boolean): ArrResult<WriteOutcome> {
        val current = store.get(serverId, METADATA_TYPE, id.toString(), MetadataResource.serializer())
            ?: return ArrResult.err(ArrError.Http(404, null))
        return saveMetadata(current.copy(enable = enabled))
    }

    suspend fun removeMetadata(id: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        deleteThrough(
            call = { metadataApi.delete(id) },
            path = "$METADATA_PATH/$id",
            optimistic = { store.delete(serverId, METADATA_TYPE, id.toString()) },
            description = "Remove metadata consumer $id",
        )
    }

    suspend fun testMetadata(resource: MetadataResource): ArrResult<Unit> = withContext(ioContext) {
        testThrough { metadataApi.test(body = resource) }
    }

    // ------------------------------------------------------------------ replay

    /**
     * Replays this group's queued provider writes through the same APIs the
     * editors use. Redacted secret values are restored from the values
     * captured at enqueue time; a queued body whose secrets are gone (the
     * process restarted before replay) is removed from the queue instead of
     * posting placeholders to the server. Runs only when every pending
     * action is one of this group's provider writes, so a foreign action
     * keeps its attempt budget for the shared replay path. Returns null
     * when nothing was replayable.
     */
    suspend fun replayQueuedActions(): Pair<Int, Int>? = withContext(ioContext) {
        val queued = actionQueue.pending()
        if (queued.isEmpty()) return@withContext null
        if (queued.any { it.serverId != serverId || !isOwnProviderAction(it) }) {
            return@withContext null
        }
        try {
            actionQueue.replay(::executeQueued)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    /** Matches this group's provider creates, updates, and deletes only. */
    private fun isOwnProviderAction(action: QueuedAction): Boolean {
        val base = REPLAYABLE_PROVIDER_PATHS.firstOrNull {
            action.path == it || action.path.startsWith("$it/")
        } ?: return false
        if (action.path == base) return action.method == POST_METHOD
        val id = action.path.removePrefix("$base/").substringBefore('/').toIntOrNull() ?: return false
        return action.method == PUT_METHOD || action.method == DELETE_METHOD
    }

    private suspend fun executeQueued(action: QueuedAction): Boolean = try {
        executeProviderAction(action)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        false
    }

    private suspend fun executeProviderAction(action: QueuedAction): Boolean {
        val base = REPLAYABLE_PROVIDER_PATHS.first { action.path == it || action.path.startsWith("$it/") }
        val id = action.path.removePrefix("$base/").substringBefore('/').toIntOrNull()
        return when (base) {
            INDEXER_PATH -> when (action.method) {
                DELETE_METHOD -> replayDelete(id, INDEXER_TYPE) { indexerApi.delete(it) }

                else -> replayProviderBody(
                    action, IndexerResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved -> store.put(serverId, INDEXER_TYPE, saved.id.toString(), saved, IndexerResource.serializer()) },
                ) { body ->
                    if (id == null) indexerApi.create(body = body) else indexerApi.update(id = id, body = body)
                }
            }

            DOWNLOAD_CLIENT_PATH -> when (action.method) {
                DELETE_METHOD -> replayDelete(id, DOWNLOAD_CLIENT_TYPE) { downloadClientApi.delete(it) }

                else -> replayProviderBody(
                    action, DownloadClientResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        store.put(
                            serverId, DOWNLOAD_CLIENT_TYPE, saved.id.toString(),
                            saved, DownloadClientResource.serializer(),
                        )
                    },
                ) { body ->
                    if (id == null) {
                        downloadClientApi.create(body = body)
                    } else {
                        downloadClientApi.update(id = id, body = body)
                    }
                }
            }

            IMPORT_LIST_PATH -> when (action.method) {
                DELETE_METHOD -> replayDelete(id, IMPORT_LIST_TYPE) { importListApi.delete(it) }

                else -> replayProviderBody(
                    action, ImportListResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        store.put(
                            serverId, IMPORT_LIST_TYPE, saved.id.toString(),
                            saved, ImportListResource.serializer(),
                        )
                    },
                ) { body ->
                    if (id == null) {
                        importListApi.create(body = body)
                    } else {
                        importListApi.update(id = id, body = body)
                    }
                }
            }

            NOTIFICATION_PATH -> when (action.method) {
                DELETE_METHOD -> replayDelete(id, NOTIFICATION_TYPE) { notificationApi.delete(it) }

                else -> replayProviderBody(
                    action, NotificationResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        store.put(
                            serverId, NOTIFICATION_TYPE, saved.id.toString(),
                            saved, NotificationResource.serializer(),
                        )
                    },
                ) { body ->
                    if (id == null) {
                        notificationApi.create(body = body)
                    } else {
                        notificationApi.update(id = id, body = body)
                    }
                }
            }

            METADATA_PATH -> when (action.method) {
                DELETE_METHOD -> replayDelete(id, METADATA_TYPE) { metadataApi.delete(it) }

                else -> replayProviderBody(
                    action, MetadataResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        store.put(
                            serverId, METADATA_TYPE, saved.id.toString(),
                            saved, MetadataResource.serializer(),
                        )
                    },
                ) { body ->
                    if (id == null) metadataApi.create(body = body) else metadataApi.update(id = id, body = body)
                }
            }

            else -> false
        }
    }

    /** Replays one queued provider delete; these APIs throw instead of answering a status. */
    private suspend fun replayDelete(
        id: Int?,
        type: EntityType,
        call: suspend (Int) -> Any?,
    ): Boolean {
        if (id == null) return false
        call(id)
        store.delete(serverId, type, id.toString())
        return true
    }

    /**
     * Decodes one queued provider body, restores its redacted secrets from
     * the live vault, executes it, and caches the redacted server row.
     * Returns false (and drops the action) when the real values are gone.
     */
    private suspend fun <T> replayProviderBody(
        action: QueuedAction,
        serializer: kotlinx.serialization.KSerializer<T>,
        fieldsOf: (T) -> List<Field>?,
        withFields: (T, List<Field>?) -> T,
        cache: suspend (T) -> Unit,
        execute: suspend (T) -> T?,
    ): Boolean {
        val queued = runCatching {
            ArrJson.decodeFromString(serializer, action.bodyJson.orEmpty())
        }.getOrNull() ?: return false
        val resolved = resolveQueuedSecrets(action, queued, fieldsOf, withFields)
        if (resolved == null) {
            // The values left memory with the process that enqueued this
            // action; failing outright beats posting placeholders.
            actionQueue.remove(action.actionId)
            return false
        }
        val saved = execute(resolved) ?: return false
        cache(withFields(saved, redactedFields(fieldsOf(saved))))
        return true
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

    /**
     * Optimistic cache row for a queued write. Creates land under a fresh
     * temporary id stamped into the row so list keys, queued badges, and a
     * follow-up delete all agree; updates keep their real id.
     */
    private suspend fun <T> putOptimistic(
        type: EntityType,
        isNew: Boolean,
        resource: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        idOf: (T) -> String,
        withTempId: (T, Int) -> T,
    ) {
        if (isNew) {
            val tempId = tempIds.getAndDecrement().toInt()
            store.put(serverId, type, tempId.toString(), withTempId(resource, tempId), serializer)
        } else {
            store.put(serverId, type, idOf(resource), resource, serializer)
        }
    }

    private suspend fun <T> writeThrough(
        call: suspend () -> T,
        method: String,
        path: String,
        body: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        cache: suspend (T) -> Unit,
        cacheQueued: suspend (T) -> Unit,
        description: String,
        secretsOf: (T) -> Map<String, JsonElement> = { emptyMap() },
        sanitize: (T) -> T = { it },
    ): ArrResult<WriteOutcome> {
        val saved = try {
            call()
        } catch (e: IOException) {
            return enqueueWrite(
                method, path, body, serializer, cacheQueued, description,
                ArrError.Network(e), secretsOf, sanitize,
            )
        } catch (e: HttpException) {
            return ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return ArrResult.err(ArrError.Unknown(e))
        }
        // The server echoes real secret values back; only a redacted copy
        // may enter the cache.
        cache(sanitize(saved))
        return ArrResult.ok(WriteOutcome.Synced)
    }

    private suspend fun deleteThrough(
        call: suspend () -> Unit,
        path: String,
        optimistic: suspend () -> Unit,
        description: String,
    ): ArrResult<WriteOutcome> {
        try {
            call()
        } catch (e: IOException) {
            val error = ArrError.Network(e)
            val action = QueuedAction(
                actionId = ActionId(UUID.nameUUIDFromBytes(
                    "$serverId:$DELETE_METHOD:$path".toByteArray(Charsets.UTF_8),
                ).toString()),
                serverId = serverId,
                appType = AppType.SONARR,
                method = DELETE_METHOD,
                path = path,
                description = description,
                enqueuedAtMillis = now() + writeSequence.getAndIncrement(),
            )
            optimistic()
            actionQueue.enqueue(action)
            return ArrResult.ok(WriteOutcome.Queued(action.actionId))
        } catch (e: HttpException) {
            return ArrResult.err(e.toArrError())
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
        serializer: kotlinx.serialization.KSerializer<T>,
        cache: suspend (T) -> Unit,
        description: String,
        error: ArrError,
        secretsOf: (T) -> Map<String, JsonElement> = { emptyMap() },
        sanitize: (T) -> T = { it },
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        // The queued copy and the optimistic cache row carry only the
        // sentinel; the real secret values are vaulted in memory keyed by
        // the action id, so replay can restore them without any persistent
        // copy ever existing.
        val redactedBody = sanitize(body)
        val action = QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$redactedBody".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.SONARR,
            method = method,
            path = path,
            bodyJson = ArrJson.encodeToString(serializer, redactedBody),
            description = description,
            // The queue orders by timestamp and breaks ties on the random
            // action id, so rapid queued writes get a strictly increasing
            // stamp to keep their replay order.
            enqueuedAtMillis = now() + writeSequence.getAndIncrement(),
        )
        secretsOf(body).takeIf { it.isNotEmpty() }?.let { secrets ->
            liveSecrets["$ACTION_KEY_PREFIX${action.actionId.value}"] = secrets
        }
        cache(redactedBody)
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Drops a queued create whose body matches, so an undone create never replays. */
    private suspend fun <T> cancelPendingCreate(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        matches: (T) -> Boolean,
    ) {
        actionQueue.pending()
            .filter { it.serverId == serverId && it.method == POST_METHOD && it.path == path }
            .forEach { action ->
                val body = action.bodyJson?.let { json ->
                    runCatching { ArrJson.decodeFromString(serializer, json) }.getOrNull()
                } ?: return@forEach
                if (matches(body)) actionQueue.remove(action.actionId)
            }
    }

    /** Single-provider test or test-all: answers a verdict, never queues. */
    private suspend fun testThrough(call: suspend () -> Unit): ArrResult<Unit> = try {
        call()
        ArrResult.ok(Unit)
    } catch (e: IOException) {
        ArrResult.err(ArrError.Network(e))
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: Throwable) {
        ArrResult.err(ArrError.Unknown(e))
    }

    private suspend fun <T> replace(
        type: EntityType,
        values: List<T>,
        serializer: kotlinx.serialization.KSerializer<T>,
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
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: Throwable) {
        ArrResult.err(ArrError.Unknown(e))
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val INDEXER_TYPE = EntityType("sonarr.indexer")
        internal val INDEXER_SCHEMA_TYPE = EntityType("sonarr.indexer.schema")
        internal val INDEXER_CONFIG_TYPE = EntityType("sonarr.indexer.config")
        internal val DOWNLOAD_CLIENT_TYPE = EntityType("sonarr.downloadclient")
        internal val DOWNLOAD_CLIENT_SCHEMA_TYPE = EntityType("sonarr.downloadclient.schema")
        internal val DOWNLOAD_CLIENT_CONFIG_TYPE = EntityType("sonarr.downloadclient.config")
        internal val REMOTE_PATH_MAPPING_TYPE = EntityType("sonarr.remotepathmapping")
        internal val IMPORT_LIST_TYPE = EntityType("sonarr.importlist")
        internal val IMPORT_LIST_SCHEMA_TYPE = EntityType("sonarr.importlist.schema")
        internal val IMPORT_LIST_CONFIG_TYPE = EntityType("sonarr.importlist.config")
        internal val EXCLUSION_TYPE = EntityType("sonarr.importlistexclusion")
        internal val NOTIFICATION_TYPE = EntityType("sonarr.notification")
        internal val NOTIFICATION_SCHEMA_TYPE = EntityType("sonarr.notification.schema")
        internal val METADATA_TYPE = EntityType("sonarr.metadata")
        internal val METADATA_SCHEMA_TYPE = EntityType("sonarr.metadata.schema")
        internal val TAG_TYPE = EntityType("sonarr.tag")

        internal const val SINGLE_ID = "1"

        /** One minute; settings screens also re-read on pull-to-refresh. */
        const val CONNECT_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** A day; provider definitions change only on server upgrades. */
        const val SCHEMA_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** Five minutes, matching the other settings groups' config rows. */
        const val TAG_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        internal const val IMPORT_LIST_SYNC_COMMAND = "ImportListSync"

        internal const val INDEXER_PATH = "api/v3/indexer"
        internal const val INDEXER_CONFIG_PATH = "api/v3/config/indexer"
        internal const val DOWNLOAD_CLIENT_PATH = "api/v3/downloadclient"
        internal const val DOWNLOAD_CLIENT_CONFIG_PATH = "api/v3/config/downloadclient"
        internal const val REMOTE_PATH_MAPPING_PATH = "api/v3/remotepathmapping"
        internal const val IMPORT_LIST_PATH = "api/v3/importlist"
        internal const val IMPORT_LIST_CONFIG_PATH = "api/v3/config/importlist"
        internal const val EXCLUSION_PATH = "api/v3/importlistexclusion"
        internal const val NOTIFICATION_PATH = "api/v3/notification"
        internal const val METADATA_PATH = "api/v3/metadata"

        /**
         * Placeholder written over secret field values in every cached or
         * queued copy. Never send it to a server: restore the real value
         * first or fail the write.
         */
        internal const val SECRET_SENTINEL = "__SECRET__"

        private const val ACTION_KEY_PREFIX = "action:"
        private const val PROVIDER_KEY_PREFIX = "provider:"

        /** Provider write paths this repository can replay on its own. */
        private val REPLAYABLE_PROVIDER_PATHS = listOf(
            INDEXER_PATH,
            DOWNLOAD_CLIENT_PATH,
            IMPORT_LIST_PATH,
            NOTIFICATION_PATH,
            METADATA_PATH,
        )
        private val FEATURE_PATHS = listOf(
            INDEXER_PATH,
            INDEXER_CONFIG_PATH,
            DOWNLOAD_CLIENT_PATH,
            DOWNLOAD_CLIENT_CONFIG_PATH,
            REMOTE_PATH_MAPPING_PATH,
            IMPORT_LIST_PATH,
            IMPORT_LIST_CONFIG_PATH,
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
