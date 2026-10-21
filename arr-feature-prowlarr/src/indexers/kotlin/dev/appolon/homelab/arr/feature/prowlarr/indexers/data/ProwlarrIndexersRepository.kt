package dev.appolon.homelab.arr.feature.prowlarr.indexers.data

import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.api.prowlarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ProwlarrCommandRunner
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import retrofit2.Response

/** How a write landed: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * Indexer feature repository. Reads are cache-first through the [EntityStore]
 * with fetchedAt TTLs per entity type; writes go to the server first and fall
 * back to optimistic cache application plus [ActionQueue] enqueue when the
 * failure is retryable ([ArrError.Offline] or [ArrError.Network]). Tests are
 * transient probes and never queue. Everything runs on IO; no function throws.
 */
class ProwlarrIndexersRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val indexerApi: suspend () -> List<IndexerResource>,
    private val indexerById: suspend (Int) -> IndexerResource,
    private val indexerSchemasApi: suspend () -> List<IndexerResource>,
    private val createIndexerApi: suspend (IndexerResource) -> IndexerResource,
    private val updateIndexerApi: suspend (Int, IndexerResource) -> IndexerResource,
    private val bulkUpdateIndexersApi: suspend (IndexerBulkResource) -> IndexerResource,
    private val deleteIndexerApi: suspend (Int) -> Response<Unit>,
    private val bulkDeleteIndexersApi: suspend (IndexerBulkResource) -> Response<Unit>,
    private val testIndexerApi: suspend (IndexerResource) -> Response<Unit>,
    private val testAllIndexersApi: suspend () -> Response<Unit>,
    private val statusApi: suspend () -> List<IndexerStatusResource>,
    private val categoriesApi: suspend () -> List<IndexerCategory>,
    private val proxyListApi: suspend () -> List<IndexerProxyResource>,
    private val proxySchemasApi: suspend () -> List<IndexerProxyResource>,
    private val createProxyApi: suspend (IndexerProxyResource) -> IndexerProxyResource,
    private val updateProxyApi: suspend (Int, IndexerProxyResource) -> IndexerProxyResource,
    private val deleteProxyApi: suspend (Int) -> Response<Unit>,
    private val testProxyApi: suspend (IndexerProxyResource) -> Response<Unit>,
    private val appProfileListApi: suspend () -> List<AppProfileResource>,
    private val createAppProfileApi: suspend (AppProfileResource) -> AppProfileResource,
    private val updateAppProfileApi: suspend (Int, AppProfileResource) -> AppProfileResource,
    private val deleteAppProfileApi: suspend (Int) -> Response<Unit>,
    private val applicationListApi: suspend () -> List<ApplicationResource>,
    private val applicationSchemasApi: suspend () -> List<ApplicationResource>,
    private val createApplicationApi: suspend (ApplicationResource) -> ApplicationResource,
    private val updateApplicationApi: suspend (Int, ApplicationResource) -> ApplicationResource,
    private val deleteApplicationApi: suspend (Int) -> Response<Unit>,
    private val testApplicationApi: suspend (ApplicationResource) -> Response<Unit>,
    private val tagListApi: suspend () -> List<TagResource>,
    private val createTagApi: suspend (TagResource) -> TagResource,
    private val commandRunner: ProwlarrCommandRunner,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeIndexers(): Flow<List<IndexerResource>> =
        store.observe(serverId, INDEXER_TYPE, IndexerResource.serializer())

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
        field.privacy == PrivacyLevel.PASSWORD || field.privacy == PrivacyLevel.API_KEY

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

    private fun sanitizeProxy(r: IndexerProxyResource) = r.copy(fields = redactedFields(r.fields))

    private fun sanitizeApplication(r: ApplicationResource) = r.copy(fields = redactedFields(r.fields))

    suspend fun cachedIndexers(): List<IndexerResource> =
        store.getAll(serverId, INDEXER_TYPE, IndexerResource.serializer())

    suspend fun isStale(ttlMillis: Long = INDEXER_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, INDEXER_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /**
     * Fetches indexers, statuses, and tags together: the list screen needs all
     * three, and one refresh keeps their stamps aligned. Failures of the side
     * collections never fail the refresh.
     */
    suspend fun refresh(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            indexerApi()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        // Fetched rows carry real secret values on the wire; remember them
        // for this session and cache only redacted copies.
        fetched.forEach { rememberProviderSecrets("$INDEXER_PATH/${it.id}", it.fields) }
        reconcile(INDEXER_TYPE, IndexerResource.serializer(), { it.id.toString() }, fetched.map(::sanitizeIndexer))
        refreshStatuses()
        refreshTags()
        // A successful refresh proves this server reachable, so this
        // feature's queued provider writes replay right here, restoring
        // their redacted secrets from the live vault, instead of waiting
        // for the sync screen's generic executor that cannot.
        replayQueuedActions()
        ArrResult.ok(Unit)
    }

    suspend fun refreshIfStale(ttlMillis: Long = INDEXER_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    fun observeStatuses(): Flow<List<IndexerStatusResource>> =
        store.observe(serverId, STATUS_TYPE, IndexerStatusResource.serializer())

    suspend fun refreshStatuses(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            statusApi()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        store.deleteAll(serverId, STATUS_TYPE)
        store.putAll(
            serverId, STATUS_TYPE,
            fetched.map { it.indexerId.toString() to it },
            IndexerStatusResource.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    fun observeTags(): Flow<List<TagResource>> =
        store.observe(serverId, TAG_TYPE, TagResource.serializer())

    /** Tag list for pickers; cached with its own TTL. */
    suspend fun tags(ttlMillis: Long = PROFILE_TTL_MILLIS): ArrResult<List<TagResource>> =
        listWithTtl(
            tagListApi, TAG_TYPE,
            TagResource.serializer(), { it.id.toString() }, ttlMillis,
        )

    suspend fun refreshTags(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            tagListApi()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        reconcile(TAG_TYPE, TagResource.serializer(), { it.id.toString() }, fetched)
        ArrResult.ok(Unit)
    }

    /** Resolves tag labels to ids, creating tags the server does not have yet. */
    suspend fun ensureTagIds(labels: List<String>): ArrResult<List<Int>> = withContext(io) {
        val wanted = labels.map(String::trim).filter(String::isNotEmpty).distinct()
        val existing = store.getAll(serverId, TAG_TYPE, TagResource.serializer())
        val byLabel = existing.associateBy { it.label.orEmpty() }
        val ids = mutableListOf<Int>()
        val created = mutableListOf<TagResource>()
        for (label in wanted) {
            val found = byLabel[label]
            if (found != null) {
                ids += found.id
            } else {
                val tag = try {
                    createTagApi(TagResource(label = label))
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
                serverId, TAG_TYPE,
                created.map { it.id.toString() to it },
                TagResource.serializer(),
                fetchedAtMillis = store.lastFetchedAt(serverId, TAG_TYPE) ?: now(),
            )
        }
        ArrResult.ok(ids.toList())
    }

    /** Prescreened definitions for the add flow; cached for a day. */
    suspend fun schemas(ttlMillis: Long = SCHEMA_TTL_MILLIS): ArrResult<List<IndexerResource>> =
        listWithTtl(
            indexerSchemasApi, SCHEMA_TYPE,
            IndexerResource.serializer(), { it.definitionName ?: it.id.toString() }, ttlMillis,
            ::sanitizeIndexer,
        )

    /** Server default category tree backing the categories manager and editor. */
    suspend fun defaultCategories(
        ttlMillis: Long = CATEGORY_TTL_MILLIS,
    ): ArrResult<List<IndexerCategory>> = listWithTtl(
        categoriesApi, CATEGORY_TYPE,
        IndexerCategory.serializer(), { it.id.toString() }, ttlMillis,
    )

    /** One indexer by id, cache first so the edit screen opens instantly. */
    suspend fun indexer(id: Int): ArrResult<IndexerResource> = withContext(io) {
        val cached = store.get(serverId, INDEXER_TYPE, id.toString(), IndexerResource.serializer())
        if (cached != null) return@withContext ArrResult.ok(cached)
        try {
            ArrResult.ok(indexerById(id))
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    suspend fun setEnabled(indexerId: Int, enabled: Boolean): ArrResult<WriteOutcome> {
        val current = when (val found = indexer(indexerId)) {
            is ArrResult.Ok -> found.value
            is ArrResult.Err -> return ArrResult.err(found.error)
        }
        return saveIndexer(current.copy(enable = enabled))
    }

    /**
     * Creates (id 0) or updates one indexer. Offline creates queue without an
     * optimistic row: the server assigns the id, so guessing one would lie.
     */
    suspend fun saveIndexer(resource: IndexerResource): ArrResult<WriteOutcome> =
        withContext(io) {
            val path = if (resource.id == 0) INDEXER_PATH else "$INDEXER_PATH/${resource.id}"
            val resolved = when (
                val r = resolveSecrets(
                    resource,
                    rememberUnder = if (resource.id == 0) null else path,
                    fieldsOf = { it.fields },
                    withFields = { res, fields -> res.copy(fields = fields) },
                )
            ) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            try {
                val saved = if (resource.id == 0) {
                    createIndexerApi(resolved)
                } else {
                    updateIndexerApi(resolved.id, resolved)
                }
                putCarryingStamp(
                    INDEXER_TYPE, IndexerResource.serializer(),
                    saved.id.toString(), sanitizeIndexer(saved),
                )
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                offlineOrErr(e.toArrError()) { queueIndexerWrite(resolved) }
            } catch (e: Throwable) {
                offlineOrErr(e.arrError()) { queueIndexerWrite(resolved) }
            }
        }

    /**
     * Bulk edit with ApplyTags semantics. The endpoint returns only the first
     * touched row, so the cache is merged with the same rules the server
     * applies, offline and online alike.
     */
    suspend fun bulkEdit(bulk: IndexerBulkResource): ArrResult<WriteOutcome> = withContext(io) {
        try {
            bulkUpdateIndexersApi(bulk)
            applyBulkOptimistic(bulk)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError()) {
                applyBulkOptimistic(bulk)
                enqueue(
                    method = "PUT", path = INDEXER_BULK_PATH,
                    bodyJson = ArrJson.encodeToString(IndexerBulkResource.serializer(), bulk),
                    description = "Bulk edit indexers",
                )
            }
        } catch (e: Throwable) {
            offlineOrErr(e.arrError()) {
                applyBulkOptimistic(bulk)
                enqueue(
                    method = "PUT", path = INDEXER_BULK_PATH,
                    bodyJson = ArrJson.encodeToString(IndexerBulkResource.serializer(), bulk),
                    description = "Bulk edit indexers",
                )
            }
        }
    }

    /** Deletes one (single endpoint) or many (bulk endpoint) indexers. */
    suspend fun deleteIndexers(ids: List<Int>): ArrResult<WriteOutcome> = withContext(io) {
        if (ids.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val single = ids.size == 1
        try {
            val response = if (single) {
                deleteIndexerApi(ids.first())
            } else {
                bulkDeleteIndexersApi(IndexerBulkResource(ids = ids))
            }
            if (!response.isSuccessful) {
                return@withContext offlineOrErr(response.toArrError()) {
                    deleteOptimistic(ids)
                    queueDelete(ids, single)
                }
            }
            deleteOptimistic(ids)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError()) {
                deleteOptimistic(ids)
                queueDelete(ids, single)
            }
        } catch (e: Throwable) {
            offlineOrErr(e.arrError()) {
                deleteOptimistic(ids)
                queueDelete(ids, single)
            }
        }
    }

    /** Probe endpoints: they answer a verdict, they never queue. */
    suspend fun testIndexer(resource: IndexerResource): ArrResult<Unit> = withContext(io) {
        responseOf { testIndexerApi(resource) }
    }

    suspend fun testAllIndexers(): ArrResult<Unit> = withContext(io) {
        responseOf { testAllIndexersApi() }
    }

    fun observeProxies(): Flow<List<IndexerProxyResource>> =
        store.observe(serverId, PROXY_TYPE, IndexerProxyResource.serializer())

    suspend fun refreshProxies(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            proxyListApi()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        fetched.forEach { rememberProviderSecrets("$PROXY_PATH/${it.id}", it.fields) }
        reconcile(PROXY_TYPE, IndexerProxyResource.serializer(), { it.id.toString() }, fetched.map(::sanitizeProxy))
        replayQueuedActions()
        ArrResult.ok(Unit)
    }

    suspend fun proxySchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<IndexerProxyResource>> = listWithTtl(
        proxySchemasApi, PROXY_SCHEMA_TYPE,
        IndexerProxyResource.serializer(), { it.implementation ?: it.id.toString() }, ttlMillis,
        ::sanitizeProxy,
    )

    suspend fun saveProxy(resource: IndexerProxyResource): ArrResult<WriteOutcome> =
        withContext(io) {
            val path = if (resource.id == 0) PROXY_PATH else "$PROXY_PATH/${resource.id}"
            val resolved = when (
                val r = resolveSecrets(
                    resource,
                    rememberUnder = if (resource.id == 0) null else path,
                    fieldsOf = { it.fields },
                    withFields = { res, fields -> res.copy(fields = fields) },
                )
            ) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            try {
                val saved = if (resource.id == 0) {
                    createProxyApi(resolved)
                } else {
                    updateProxyApi(resolved.id, resolved)
                }
                putCarryingStamp(
                    PROXY_TYPE, IndexerProxyResource.serializer(),
                    saved.id.toString(), sanitizeProxy(saved),
                )
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                offlineOrErr(e.toArrError()) { queueProxyWrite(resolved) }
            } catch (e: Throwable) {
                offlineOrErr(e.arrError()) { queueProxyWrite(resolved) }
            }
        }

    suspend fun deleteProxy(id: Int): ArrResult<WriteOutcome> = withContext(io) {
        try {
            val response = deleteProxyApi(id)
            if (!response.isSuccessful) {
                return@withContext offlineOrErr(response.toArrError()) { queueProxyDelete(id) }
            }
            store.delete(serverId, PROXY_TYPE, id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError()) { queueProxyDelete(id) }
        } catch (e: Throwable) {
            offlineOrErr(e.arrError()) { queueProxyDelete(id) }
        }
    }

    suspend fun testProxy(resource: IndexerProxyResource): ArrResult<Unit> = withContext(io) {
        responseOf { testProxyApi(resource) }
    }

    suspend fun appProfiles(
        ttlMillis: Long = PROFILE_TTL_MILLIS,
    ): ArrResult<List<AppProfileResource>> = listWithTtl(
        appProfileListApi, APP_PROFILE_TYPE,
        AppProfileResource.serializer(), { it.id.toString() }, ttlMillis,
    )

    suspend fun saveAppProfile(resource: AppProfileResource): ArrResult<WriteOutcome> =
        withContext(io) {
            try {
                val saved = if (resource.id == 0) {
                    createAppProfileApi(resource)
                } else {
                    updateAppProfileApi(resource.id, resource)
                }
                putCarryingStamp(
                    APP_PROFILE_TYPE, AppProfileResource.serializer(),
                    saved.id.toString(), saved,
                )
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                offlineOrErr(e.toArrError()) { queueAppProfileWrite(resource) }
            } catch (e: Throwable) {
                offlineOrErr(e.arrError()) { queueAppProfileWrite(resource) }
            }
        }

    suspend fun deleteAppProfile(id: Int): ArrResult<WriteOutcome> = withContext(io) {
        try {
            val response = deleteAppProfileApi(id)
            if (!response.isSuccessful) {
                return@withContext offlineOrErr(response.toArrError()) { queueAppProfileDelete(id) }
            }
            store.delete(serverId, APP_PROFILE_TYPE, id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError()) { queueAppProfileDelete(id) }
        } catch (e: Throwable) {
            offlineOrErr(e.arrError()) { queueAppProfileDelete(id) }
        }
    }

    fun observeApplications(): Flow<List<ApplicationResource>> =
        store.observe(serverId, APPLICATION_TYPE, ApplicationResource.serializer())

    suspend fun refreshApplications(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            applicationListApi()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        fetched.forEach { rememberProviderSecrets("$APPLICATION_PATH/${it.id}", it.fields) }
        reconcile(
            APPLICATION_TYPE, ApplicationResource.serializer(), { it.id.toString() },
            fetched.map(::sanitizeApplication),
        )
        replayQueuedActions()
        ArrResult.ok(Unit)
    }

    suspend fun applicationSchemas(
        ttlMillis: Long = SCHEMA_TTL_MILLIS,
    ): ArrResult<List<ApplicationResource>> = listWithTtl(
        applicationSchemasApi, APPLICATION_SCHEMA_TYPE,
        ApplicationResource.serializer(), { it.implementation ?: it.id.toString() }, ttlMillis,
        ::sanitizeApplication,
    )

    suspend fun saveApplication(resource: ApplicationResource): ArrResult<WriteOutcome> =
        withContext(io) {
            val path = if (resource.id == 0) APPLICATION_PATH else "$APPLICATION_PATH/${resource.id}"
            val resolved = when (
                val r = resolveSecrets(
                    resource,
                    rememberUnder = if (resource.id == 0) null else path,
                    fieldsOf = { it.fields },
                    withFields = { res, fields -> res.copy(fields = fields) },
                )
            ) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            try {
                val saved = if (resource.id == 0) {
                    createApplicationApi(resolved)
                } else {
                    updateApplicationApi(resolved.id, resolved)
                }
                putCarryingStamp(
                    APPLICATION_TYPE, ApplicationResource.serializer(),
                    saved.id.toString(), sanitizeApplication(saved),
                )
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                offlineOrErr(e.toArrError()) { queueApplicationWrite(resolved) }
            } catch (e: Throwable) {
                offlineOrErr(e.arrError()) { queueApplicationWrite(resolved) }
            }
        }

    suspend fun deleteApplication(id: Int): ArrResult<WriteOutcome> = withContext(io) {
        try {
            val response = deleteApplicationApi(id)
            if (!response.isSuccessful) {
                return@withContext offlineOrErr(response.toArrError()) { queueApplicationDelete(id) }
            }
            store.delete(serverId, APPLICATION_TYPE, id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError()) { queueApplicationDelete(id) }
        } catch (e: Throwable) {
            offlineOrErr(e.arrError()) { queueApplicationDelete(id) }
        }
    }

    suspend fun testApplication(resource: ApplicationResource): ArrResult<Unit> =
        withContext(io) {
            responseOf { testApplicationApi(resource) }
        }

    /**
     * Pushes configured indexers to every application. The command replays
     * from the queue when offline, unlike single probes.
     */
    suspend fun syncApplications(): ArrResult<WriteOutcome> = withContext(io) {
        val error = try {
            val result = commandRunner.run(SYNC_COMMAND)
            if (result is ArrResult.Ok) {
                return@withContext ArrResult.ok(WriteOutcome.Synced)
            }
            (result as ArrResult.Err).error
        } catch (e: Throwable) {
            e.arrError()
        }
        if (!error.isRetryable()) {
            return@withContext ArrResult.err(error)
        }
        val actionId = enqueue(
            method = "POST", path = COMMAND_PATH,
            bodyJson = "{\"name\":\"$SYNC_COMMAND\"}",
            description = "Sync applications",
        )
        ArrResult.ok(WriteOutcome.Queued(actionId))
    }

    /** Indexer ids touched by queued indexer actions, for queued badges. */
    fun queuedIndexerIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path.startsWith(INDEXER_PATH_PREFIX) }
            .flatMap { action ->
                when {
                    action.method == "PUT" && action.path == INDEXER_BULK_PATH ->
                        decodeBulkIds(action.bodyJson)

                    action.method == "DELETE" && action.path == INDEXER_BULK_PATH ->
                        decodeBulkIds(action.bodyJson)

                    action.method == "PUT" && action.path.startsWith(INDEXER_ID_PREFIX) ->
                        action.path.removePrefix(INDEXER_ID_PREFIX).toIntOrNull()?.let(::listOf)
                            .orEmpty()

                    action.method == "DELETE" && action.path.startsWith(INDEXER_ID_PREFIX) ->
                        action.path.removePrefix(INDEXER_ID_PREFIX).toIntOrNull()?.let(::listOf)
                            .orEmpty()

                    else -> emptyList()
                }
            }
            .toSet()
    }

    // ------------------------------------------------------------------ replay

    /**
     * Replays this feature's queued provider writes (indexers, proxies,
     * applications) through the same APIs the editors use. Redacted secret
     * values are restored from the values captured at enqueue time; a
     * queued body whose secrets are gone (the process restarted before
     * replay) is removed from the queue instead of posting placeholders to
     * the server. Runs only when every pending action is one of this
     * feature's provider writes, so a foreign action keeps its attempt
     * budget for the shared replay path. Returns null when nothing was
     * replayable.
     */
    suspend fun replayQueuedActions(): Pair<Int, Int>? = withContext(io) {
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

    /** Matches this feature's provider creates, updates, and deletes only. */
    private fun isOwnProviderAction(action: QueuedAction): Boolean {
        val base = REPLAYABLE_PROVIDER_PATHS.firstOrNull {
            action.path == it || action.path.startsWith("$it/")
        } ?: return false
        if (action.path == base) return action.method == "POST"
        val id = action.path.removePrefix("$base/").substringBefore('/').toIntOrNull() ?: return false
        return action.method == "PUT" || action.method == "DELETE"
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
                "DELETE" -> id != null && deleteIndexerApi(id).isSuccessful.also {
                    if (it) store.delete(serverId, INDEXER_TYPE, id.toString())
                }

                else -> replayProviderBody(
                    action, IndexerResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        putCarryingStamp(
                            INDEXER_TYPE, IndexerResource.serializer(),
                            saved.id.toString(), saved,
                        )
                    },
                ) { body ->
                    if (id == null) createIndexerApi(body) else updateIndexerApi(id, body)
                }
            }

            PROXY_PATH -> when (action.method) {
                "DELETE" -> id != null && deleteProxyApi(id).isSuccessful.also {
                    if (it) store.delete(serverId, PROXY_TYPE, id.toString())
                }

                else -> replayProviderBody(
                    action, IndexerProxyResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        putCarryingStamp(
                            PROXY_TYPE, IndexerProxyResource.serializer(),
                            saved.id.toString(), saved,
                        )
                    },
                ) { body ->
                    if (id == null) createProxyApi(body) else updateProxyApi(id, body)
                }
            }

            APPLICATION_PATH -> when (action.method) {
                "DELETE" -> id != null && deleteApplicationApi(id).isSuccessful.also {
                    if (it) store.delete(serverId, APPLICATION_TYPE, id.toString())
                }

                else -> replayProviderBody(
                    action, ApplicationResource.serializer(),
                    { it.fields }, { r, f -> r.copy(fields = f) },
                    { saved ->
                        putCarryingStamp(
                            APPLICATION_TYPE, ApplicationResource.serializer(),
                            saved.id.toString(), saved,
                        )
                    },
                ) { body ->
                    if (id == null) createApplicationApi(body) else updateApplicationApi(id, body)
                }
            }

            else -> false
        }
    }

    /**
     * Decodes one queued provider body, restores its redacted secrets from
     * the live vault, executes it, and caches the redacted server row.
     * Returns false (and drops the action) when the real values are gone.
     */
    private suspend fun <T> replayProviderBody(
        action: QueuedAction,
        serializer: KSerializer<T>,
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

    /** Count of queued actions for this server, a whole screen indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    private fun decodeBulkIds(bodyJson: String?): List<Int> =
        runCatching {
            ArrJson.decodeFromString(IndexerBulkResource.serializer(), bodyJson.orEmpty()).ids
        }.getOrNull().orEmpty()

    private suspend fun queueIndexerWrite(resource: IndexerResource): ActionId {
        if (resource.id != 0) {
            putCarryingStamp(
                INDEXER_TYPE, IndexerResource.serializer(),
                resource.id.toString(), sanitizeIndexer(resource),
            )
        }
        // The queued copy and the optimistic cache row carry only the
        // sentinel; the real secret values are vaulted in memory keyed by
        // the action id, so replay can restore them without any persistent
        // copy ever existing.
        val redacted = sanitizeIndexer(resource)
        val bodyJson = ArrJson.encodeToString(IndexerResource.serializer(), redacted)
        val actionId = if (resource.id == 0) {
            enqueue(
                method = "POST", path = INDEXER_PATH,
                bodyJson = bodyJson,
                description = "Add indexer ${resource.name.orEmpty()}",
            )
        } else {
            enqueue(
                method = "PUT", path = "$INDEXER_PATH/${resource.id}",
                bodyJson = bodyJson,
                description = "Edit indexer ${resource.name.orEmpty()}",
            )
        }
        secretValuesOf(resource.fields).takeIf { it.isNotEmpty() }?.let { secrets ->
            liveSecrets["$ACTION_KEY_PREFIX${actionId.value}"] = secrets
        }
        return actionId
    }

    private suspend fun queueDelete(ids: List<Int>, single: Boolean): ActionId =
        if (single) {
            enqueue(
                method = "DELETE", path = "$INDEXER_PATH/${ids.first()}",
                bodyJson = null, description = "Delete indexer",
            )
        } else {
            enqueue(
                method = "DELETE", path = INDEXER_BULK_PATH,
                bodyJson = ArrJson.encodeToString(
                    IndexerBulkResource.serializer(), IndexerBulkResource(ids = ids),
                ),
                description = "Delete indexers",
            )
        }

    private suspend fun queueProxyWrite(resource: IndexerProxyResource): ActionId {
        if (resource.id != 0) {
            putCarryingStamp(
                PROXY_TYPE, IndexerProxyResource.serializer(),
                resource.id.toString(), sanitizeProxy(resource),
            )
        }
        return queueEntityWrite(
            resource.id, IndexerProxyResource.serializer(), resource,
            PROXY_PATH, resource.name.orEmpty(), "indexer proxy",
            ::sanitizeProxy,
        ) { secretValuesOf(it.fields) }
    }

    private suspend fun queueProxyDelete(id: Int): ActionId {
        store.delete(serverId, PROXY_TYPE, id.toString())
        return enqueue("DELETE", "$PROXY_PATH/$id", null, "Delete indexer proxy")
    }

    private suspend fun queueAppProfileWrite(resource: AppProfileResource): ActionId {
        if (resource.id != 0) {
            putCarryingStamp(
                APP_PROFILE_TYPE, AppProfileResource.serializer(),
                resource.id.toString(), resource,
            )
        }
        return queueEntityWrite(
            resource.id, AppProfileResource.serializer(), resource,
            APP_PROFILE_PATH, resource.name.orEmpty(), "app profile",
        )
    }

    private suspend fun queueAppProfileDelete(id: Int): ActionId {
        store.delete(serverId, APP_PROFILE_TYPE, id.toString())
        return enqueue("DELETE", "$APP_PROFILE_PATH/$id", null, "Delete app profile")
    }

    private suspend fun queueApplicationWrite(resource: ApplicationResource): ActionId {
        if (resource.id != 0) {
            putCarryingStamp(
                APPLICATION_TYPE, ApplicationResource.serializer(),
                resource.id.toString(), sanitizeApplication(resource),
            )
        }
        return queueEntityWrite(
            resource.id, ApplicationResource.serializer(), resource,
            APPLICATION_PATH, resource.name.orEmpty(), "application",
            ::sanitizeApplication,
        ) { secretValuesOf(it.fields) }
    }

    private suspend fun queueApplicationDelete(id: Int): ActionId {
        store.delete(serverId, APPLICATION_TYPE, id.toString())
        return enqueue("DELETE", "$APPLICATION_PATH/$id", null, "Delete application")
    }

    private suspend fun <T> queueEntityWrite(
        id: Int,
        serializer: KSerializer<T>,
        resource: T,
        path: String,
        name: String,
        what: String,
        sanitize: (T) -> T = { it },
        secretsOf: (T) -> Map<String, JsonElement> = { emptyMap() },
    ): ActionId {
        // The queued copy carries only the sentinel; the real secret values
        // are vaulted in memory keyed by the action id, so replay can
        // restore them without any persistent copy ever existing.
        val redacted = sanitize(resource)
        val actionId = if (id == 0) {
            enqueue(
                method = "POST", path = path,
                bodyJson = ArrJson.encodeToString(serializer, redacted),
                description = "Add $what $name",
            )
        } else {
            enqueue(
                method = "PUT", path = "$path/$id",
                bodyJson = ArrJson.encodeToString(serializer, redacted),
                description = "Edit $what $name",
            )
        }
        secretsOf(resource).takeIf { it.isNotEmpty() }?.let { secrets ->
            liveSecrets["$ACTION_KEY_PREFIX${actionId.value}"] = secrets
        }
        return actionId
    }

    /** Merges a bulk edit into cached rows with the server's semantics. */
    private suspend fun applyBulkOptimistic(bulk: IndexerBulkResource) {
        val targets = bulk.ids.orEmpty().toSet()
        if (targets.isEmpty()) return
        val stamp = store.lastFetchedAt(serverId, INDEXER_TYPE) ?: now()
        val merged = cachedIndexers().map { current ->
            if (current.id in targets) mergeBulk(current, bulk) else current
        }
        store.putAll(
            serverId, INDEXER_TYPE,
            merged.map { it.id.toString() to it },
            IndexerResource.serializer(),
            fetchedAtMillis = stamp,
        )
    }

    private suspend fun deleteOptimistic(ids: List<Int>) {
        ids.forEach { id -> store.delete(serverId, INDEXER_TYPE, id.toString()) }
    }

    private suspend fun <T> putCarryingStamp(
        type: EntityType,
        serializer: KSerializer<T>,
        id: String,
        value: T,
    ) {
        val stamp = store.lastFetchedAt(serverId, type) ?: now()
        store.putAll(serverId, type, listOf(id to value), serializer, fetchedAtMillis = stamp)
    }

    private suspend fun <T> reconcile(
        type: EntityType,
        serializer: KSerializer<T>,
        idOf: (T) -> String,
        fetched: List<T>,
    ) {
        val incomingIds = fetched.map(idOf).toSet()
        val existingIds = store.getAll(serverId, type, serializer).map(idOf).toSet()
        existingIds.filter { it !in incomingIds }.forEach { id ->
            store.delete(serverId, type, id)
        }
        store.putAll(
            serverId, type,
            fetched.map { idOf(it) to it },
            serializer,
            fetchedAtMillis = now(),
        )
    }

    /**
     * Cached list with TTL for rarely changing reference data. A fetch
     * failure falls back to stale rows when any exist; the screens keep
     * working offline.
     */
    private suspend fun <T> listWithTtl(
        fetch: suspend () -> List<T>,
        type: EntityType,
        serializer: KSerializer<T>,
        idOf: (T) -> String,
        ttlMillis: Long,
        sanitize: (T) -> T = { it },
    ): ArrResult<List<T>> = withContext(io) {
        val cached = store.getAll(serverId, type, serializer)
        val stamp = store.lastFetchedAt(serverId, type)
        if (stamp != null && now() - stamp <= ttlMillis && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            fetch()
        } catch (e: HttpException) {
            return@withContext staleOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext staleOrErr(cached, e.arrError())
        }
        if (fetched.isEmpty()) {
            ArrResult.ok(cached)
        } else {
            reconcile(type, serializer, idOf, fetched.map(sanitize))
            ArrResult.ok(fetched)
        }
    }

    private fun <T> staleOrErr(cached: List<T>, error: ArrError): ArrResult<List<T>> =
        ArrResult.err(error)

    private suspend fun responseOf(call: suspend () -> Response<Unit>): ArrResult<Unit> {
        val response = try {
            call()
        } catch (e: HttpException) {
            return ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return ArrResult.err(e.arrError())
        }
        return if (response.isSuccessful) {
            ArrResult.ok(Unit)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    private inline fun offlineOrErr(
        error: ArrError,
        enqueueOptimistic: () -> ActionId,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val actionId = enqueueOptimistic()
        return ArrResult.ok(WriteOutcome.Queued(actionId))
    }

    private suspend fun enqueue(
        method: String,
        path: String,
        bodyJson: String?,
        description: String,
    ): ActionId {
        val action = QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$bodyJson".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.PROWLARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )
        actionQueue.enqueue(action)
        return action.actionId
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

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
        internal val INDEXER_TYPE = EntityType("prowlarr.indexer")
        internal val SCHEMA_TYPE = EntityType("prowlarr.indexer.schema")
        internal val STATUS_TYPE = EntityType("prowlarr.indexer.status")
        internal val CATEGORY_TYPE = EntityType("prowlarr.indexer.category")
        internal val TAG_TYPE = EntityType("prowlarr.tag")
        internal val PROXY_TYPE = EntityType("prowlarr.indexerproxy")
        internal val PROXY_SCHEMA_TYPE = EntityType("prowlarr.indexerproxy.schema")
        internal val APP_PROFILE_TYPE = EntityType("prowlarr.appprofile")
        internal val APPLICATION_TYPE = EntityType("prowlarr.application")
        internal val APPLICATION_SCHEMA_TYPE = EntityType("prowlarr.application.schema")

        internal const val INDEXER_PATH = "api/v1/indexer"
        internal const val INDEXER_PATH_PREFIX = "api/v1/indexer"
        internal const val INDEXER_ID_PREFIX = "api/v1/indexer/"
        internal const val INDEXER_BULK_PATH = "api/v1/indexer/bulk"
        internal const val PROXY_PATH = "api/v1/indexerproxy"
        internal const val APP_PROFILE_PATH = "api/v1/appprofile"
        internal const val APPLICATION_PATH = "api/v1/applications"
        private const val COMMAND_PATH = "api/v1/command"

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
            PROXY_PATH,
            APPLICATION_PATH,
        )

        /** The command name Prowlarr uses to push indexers to applications. */
        const val SYNC_COMMAND = "ApplicationSync"

        /** Five minutes; indexers rarely change but statuses drive badges. */
        const val INDEXER_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY

        /** One hour for profiles and tags, a day for schemas and categories. */
        const val PROFILE_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS
        const val SCHEMA_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS
        const val CATEGORY_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** Builds a repository from the API bundle of a connected server. */
        operator fun invoke(
            serverId: ServerId,
            store: EntityStore,
            apis: dev.appolon.homelab.arr.feature.prowlarr.indexers.ProwlarrIndexersFeatureApis,
            actionQueue: ActionQueue,
            now: () -> Long = System::currentTimeMillis,
            io: CoroutineDispatcher = Dispatchers.IO,
        ): ProwlarrIndexersRepository = ProwlarrIndexersRepository(
            serverId = serverId,
            store = store,
            indexerApi = apis.indexerApi::getIndexers,
            indexerById = apis.indexerApi::getIndexer,
            indexerSchemasApi = apis.indexerApi::getIndexerSchemas,
            createIndexerApi = { apis.indexerApi.createIndexer(null, it) },
            updateIndexerApi = { id, it -> apis.indexerApi.updateIndexer(id.toString(), null, it) },
            bulkUpdateIndexersApi = apis.indexerApi::updateIndexers,
            deleteIndexerApi = apis.indexerApi::deleteIndexer,
            bulkDeleteIndexersApi = apis.indexerApi::deleteIndexers,
            testIndexerApi = { apis.indexerApi.testIndexer(null, it) },
            testAllIndexersApi = apis.indexerApi::testAllIndexers,
            statusApi = apis.statusApi::getIndexerStatuses,
            categoriesApi = apis.categoriesApi::getCategories,
            proxyListApi = apis.proxyApi::getIndexerProxies,
            proxySchemasApi = apis.proxyApi::getIndexerProxySchemas,
            createProxyApi = { apis.proxyApi.createIndexerProxy(null, it) },
            updateProxyApi = { id, it ->
                apis.proxyApi.updateIndexerProxy(id.toString(), null, it)
            },
            deleteProxyApi = apis.proxyApi::deleteIndexerProxy,
            testProxyApi = { apis.proxyApi.testIndexerProxy(null, it) },
            appProfileListApi = apis.appProfileApi::getAppProfiles,
            createAppProfileApi = apis.appProfileApi::createAppProfile,
            updateAppProfileApi = { id, it ->
                apis.appProfileApi.updateAppProfile(id.toString(), it)
            },
            deleteAppProfileApi = apis.appProfileApi::deleteAppProfile,
            applicationListApi = apis.applicationApi::getApplications,
            applicationSchemasApi = apis.applicationApi::getApplicationSchemas,
            createApplicationApi = { apis.applicationApi.createApplication(null, it) },
            updateApplicationApi = { id, it ->
                apis.applicationApi.updateApplication(id.toString(), null, it)
            },
            deleteApplicationApi = apis.applicationApi::deleteApplication,
            testApplicationApi = { apis.applicationApi.testApplication(null, it) },
            tagListApi = apis.tagApi::getTags,
            createTagApi = apis.tagApi::createTag,
            commandRunner = apis.commandRunner,
            actionQueue = actionQueue,
            now = now,
            io = io,
        )
    }
}

/** Server-side merge rules of the bulk indexer endpoint, applied locally. */
internal fun mergeBulk(
    current: IndexerResource,
    bulk: IndexerBulkResource,
): IndexerResource = current.copy(
    enable = bulk.enable ?: current.enable,
    appProfileId = bulk.appProfileId ?: current.appProfileId,
    priority = bulk.priority ?: current.priority,
    tags = when (bulk.applyTags) {
        dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags.ADD ->
            (current.tags.orEmpty() + bulk.tags.orEmpty()).distinct()

        dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags.REMOVE ->
            current.tags.orEmpty() - bulk.tags.orEmpty().toSet()

        dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags.REPLACE ->
            bulk.tags ?: current.tags
    },
)
