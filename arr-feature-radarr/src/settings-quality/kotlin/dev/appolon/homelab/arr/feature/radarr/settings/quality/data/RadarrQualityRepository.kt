package dev.appolon.homelab.arr.feature.radarr.settings.quality.data

import dev.appolon.homelab.arr.api.radarr.CustomFormatApi
import dev.appolon.homelab.arr.api.radarr.DelayProfileApi
import dev.appolon.homelab.arr.api.radarr.LanguageApi
import dev.appolon.homelab.arr.api.radarr.QualityDefinitionApi
import dev.appolon.homelab.arr.api.radarr.QualityProfileApi
import dev.appolon.homelab.arr.api.radarr.QualityProfileSchemaApi
import dev.appolon.homelab.arr.api.radarr.ReleaseProfileApi
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import retrofit2.Response

/**
 * Cache-first repository for everything under Settings > Quality: quality
 * definitions and their server limits, quality profiles (plus the blank
 * schema the builder form starts from), custom formats, delay profiles,
 * release profiles, and the read-only language table.
 *
 * Reads come from the [EntityStore] with a fetchedAt TTL. Writes hit the
 * server first; when the network is unreachable the change applies
 * optimistically to the cache and enqueues for replay through the
 * [ActionQueue]. Creates made offline use a large negative temporary id so
 * they render immediately and reconcile away on the next refresh. Nothing
 * throws; everything runs on [ioContext].
 */
class RadarrQualityRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val qualityDefinitionApi: QualityDefinitionApi,
    private val qualityProfileApi: QualityProfileApi,
    private val qualityProfileSchemaApi: QualityProfileSchemaApi,
    private val customFormatApi: CustomFormatApi,
    private val delayProfileApi: DelayProfileApi,
    private val releaseProfileApi: ReleaseProfileApi,
    private val languageApi: LanguageApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    private val tempIds = AtomicLong(TEMP_ID_BASE)

    fun observeQualityDefinitions(): Flow<List<QualityDefinitionResource>> =
        store.observe(serverId, QUALITY_DEFINITION_TYPE, QualityDefinitionResource.serializer())

    fun observeQualityProfiles(): Flow<List<QualityProfileResource>> =
        store.observe(serverId, QUALITY_PROFILE_TYPE, QualityProfileResource.serializer())

    fun observeCustomFormats(): Flow<List<CustomFormatResource>> =
        store.observe(serverId, CUSTOM_FORMAT_TYPE, CustomFormatResource.serializer())

    fun observeDelayProfiles(): Flow<List<DelayProfileResource>> =
        store.observe(serverId, DELAY_PROFILE_TYPE, DelayProfileResource.serializer())

    fun observeReleaseProfiles(): Flow<List<ReleaseProfileResource>> =
        store.observe(serverId, RELEASE_PROFILE_TYPE, ReleaseProfileResource.serializer())

    fun observeLanguages(): Flow<List<LanguageResource>> =
        store.observe(serverId, LANGUAGE_TYPE, LanguageResource.serializer())

    suspend fun cachedQualityDefinitionLimits(): QualityDefinitionLimitsResource? =
        store.get(serverId, LIMITS_TYPE, SINGLE_ID, QualityDefinitionLimitsResource.serializer())

    suspend fun cachedQualityProfileSchema(): QualityProfileResource? =
        store.get(serverId, PROFILE_SCHEMA_TYPE, SINGLE_ID, QualityProfileResource.serializer())

    suspend fun cachedQualityDefinitions(): List<QualityDefinitionResource> =
        store.getAll(serverId, QUALITY_DEFINITION_TYPE, QualityDefinitionResource.serializer())

    suspend fun cachedQualityProfiles(): List<QualityProfileResource> =
        store.getAll(serverId, QUALITY_PROFILE_TYPE, QualityProfileResource.serializer())

    suspend fun cachedCustomFormats(): List<CustomFormatResource> =
        store.getAll(serverId, CUSTOM_FORMAT_TYPE, CustomFormatResource.serializer())

    suspend fun cachedDelayProfiles(): List<DelayProfileResource> =
        store.getAll(serverId, DELAY_PROFILE_TYPE, DelayProfileResource.serializer())

    suspend fun cachedReleaseProfiles(): List<ReleaseProfileResource> =
        store.getAll(serverId, RELEASE_PROFILE_TYPE, ReleaseProfileResource.serializer())

    suspend fun cachedLanguages(): List<LanguageResource> =
        store.getAll(serverId, LANGUAGE_TYPE, LanguageResource.serializer())

    /** True when any of this screen group's caches is absent or past its TTL. */
    suspend fun isStale(ttlMillis: Long = QUALITY_TTL_MILLIS): Boolean =
        listOf(
            QUALITY_DEFINITION_TYPE,
            QUALITY_PROFILE_TYPE,
            CUSTOM_FORMAT_TYPE,
            DELAY_PROFILE_TYPE,
            RELEASE_PROFILE_TYPE,
            LANGUAGE_TYPE,
        ).any { type -> isTypeStale(type, ttlMillis) }

    /**
     * Re-fetches every table this group renders. Each type reconciles: rows
     * the server no longer returns (including optimistic temporary ones)
     * drop out of the cache.
     */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        val definitions = when (val r = fetch { qualityDefinitionApi.qualityDefinitions() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val limits = when (val r = fetch { qualityDefinitionApi.qualityDefinitionLimits() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val profiles = when (val r = fetch { qualityProfileApi.qualityProfiles() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val formats = when (val r = fetch { customFormatApi.customFormats() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val delays = when (val r = fetch { delayProfileApi.delayProfiles() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val releases = when (val r = fetch { releaseProfileApi.releaseProfiles() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val languages = when (val r = fetch { languageApi.languages() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val stamp = now()
        replace(QUALITY_DEFINITION_TYPE, definitions, QualityDefinitionResource.serializer(), stamp)
        store.put(
            serverId,
            LIMITS_TYPE,
            SINGLE_ID,
            limits,
            QualityDefinitionLimitsResource.serializer(),
        )
        replace(QUALITY_PROFILE_TYPE, profiles, QualityProfileResource.serializer(), stamp)
        replace(CUSTOM_FORMAT_TYPE, formats, CustomFormatResource.serializer(), stamp)
        replace(DELAY_PROFILE_TYPE, delays, DelayProfileResource.serializer(), stamp)
        replace(RELEASE_PROFILE_TYPE, releases, ReleaseProfileResource.serializer(), stamp)
        replace(LANGUAGE_TYPE, languages.sortedBy { it.id }, LanguageResource.serializer(), stamp)
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(ttlMillis: Long = QUALITY_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /**
     * Fetches the blank profile the builder form is built from. Cached so
     * the create flow still opens offline; failures fall back to the cache.
     */
    suspend fun refreshProfileSchema(): ArrResult<QualityProfileResource> =
        withContext(ioContext) {
            val schema = when (val r = fetch { qualityProfileSchemaApi.qualityProfileSchema() }) {
                is ArrResult.Err -> return@withContext r

                is ArrResult.Ok -> r.value
            }
            store.put(
                serverId,
                PROFILE_SCHEMA_TYPE,
                SINGLE_ID,
                schema,
                QualityProfileResource.serializer(),
            )
            ArrResult.ok(schema)
        }

    /** Saves the whole quality definitions table through the bulk update endpoint. */
    suspend fun saveQualityDefinitions(
        definitions: List<QualityDefinitionResource>,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        try {
            val saved = qualityDefinitionApi.updateQualityDefinitions(definitions)
            replace(QUALITY_DEFINITION_TYPE, saved, QualityDefinitionResource.serializer(), now())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: IOException) {
            enqueueWrite(
                method = PUT_METHOD,
                path = "api/v3/qualitydefinition/update",
                body = definitions,
                serializer = ListSerializer(QualityDefinitionResource.serializer()),
                cache = {
                    replace(
                        QUALITY_DEFINITION_TYPE,
                        definitions,
                        QualityDefinitionResource.serializer(),
                        stampPreserving(QUALITY_DEFINITION_TYPE),
                    )
                },
                description = "Save quality definitions",
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            ArrResult.err(e.arrErrorOrUnknown())
        }
    }

    suspend fun saveQualityProfile(
        profile: QualityProfileResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = profile.id <= 0
        if (isNew) {
            val response = try {
                qualityProfileApi.createQualityProfile(profile)
            } catch (e: IOException) {
                return@withContext enqueueOptimisticCreate(
                    path = "api/v3/qualityprofile",
                    body = profile,
                    serializer = QualityProfileResource.serializer(),
                    type = QUALITY_PROFILE_TYPE,
                    description = "Create quality profile",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    QUALITY_PROFILE_TYPE,
                    saved.id.toString(),
                    saved,
                    QualityProfileResource.serializer(),
                )
            }
        } else {
            val response = try {
                qualityProfileApi.updateQualityProfile(profile.id.toString(), profile)
            } catch (e: IOException) {
                return@withContext enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/qualityprofile/${profile.id}",
                    body = profile,
                    serializer = QualityProfileResource.serializer(),
                    cache = {
                        putPreservingStamp(
                            QUALITY_PROFILE_TYPE,
                            profile.id.toString(),
                            profile,
                            QualityProfileResource.serializer(),
                        )
                    },
                    description = "Save quality profile",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    QUALITY_PROFILE_TYPE,
                    saved.id.toString(),
                    saved,
                    QualityProfileResource.serializer(),
                )
            }
        }
    }

    suspend fun deleteQualityProfile(profileId: Int): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            deleteWithQueue(
                call = { qualityProfileApi.removeQualityProfile(profileId) },
                type = QUALITY_PROFILE_TYPE,
                id = profileId,
                path = "api/v3/qualityprofile/$profileId",
                description = "Delete quality profile",
            )
        }

    suspend fun saveCustomFormat(
        format: CustomFormatResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = format.id <= 0
        if (isNew) {
            val response = try {
                customFormatApi.createCustomFormat(format)
            } catch (e: IOException) {
                return@withContext enqueueOptimisticCreate(
                    path = "api/v3/customformat",
                    body = format,
                    serializer = CustomFormatResource.serializer(),
                    type = CUSTOM_FORMAT_TYPE,
                    description = "Create custom format",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    CUSTOM_FORMAT_TYPE,
                    saved.id.toString(),
                    saved,
                    CustomFormatResource.serializer(),
                )
            }
        } else {
            val response = try {
                customFormatApi.updateCustomFormat(format.id.toString(), format)
            } catch (e: IOException) {
                return@withContext enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/customformat/${format.id}",
                    body = format,
                    serializer = CustomFormatResource.serializer(),
                    cache = {
                        putPreservingStamp(
                            CUSTOM_FORMAT_TYPE,
                            format.id.toString(),
                            format,
                            CustomFormatResource.serializer(),
                        )
                    },
                    description = "Save custom format",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    CUSTOM_FORMAT_TYPE,
                    saved.id.toString(),
                    saved,
                    CustomFormatResource.serializer(),
                )
            }
        }
    }

    suspend fun deleteCustomFormat(formatId: Int): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            deleteWithQueue(
                call = { customFormatApi.removeCustomFormat(formatId) },
                type = CUSTOM_FORMAT_TYPE,
                id = formatId,
                path = "api/v3/customformat/$formatId",
                description = "Delete custom format",
            )
        }

    suspend fun saveDelayProfile(
        profile: DelayProfileResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = profile.id <= 0
        if (isNew) {
            val response = try {
                delayProfileApi.createDelayProfile(profile)
            } catch (e: IOException) {
                return@withContext enqueueOptimisticCreate(
                    path = "api/v3/delayprofile",
                    body = profile,
                    serializer = DelayProfileResource.serializer(),
                    type = DELAY_PROFILE_TYPE,
                    description = "Create delay profile",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    DELAY_PROFILE_TYPE,
                    saved.id.toString(),
                    saved,
                    DelayProfileResource.serializer(),
                )
            }
        } else {
            val response = try {
                delayProfileApi.updateDelayProfile(profile.id.toString(), profile)
            } catch (e: IOException) {
                return@withContext enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/delayprofile/${profile.id}",
                    body = profile,
                    serializer = DelayProfileResource.serializer(),
                    cache = {
                        putPreservingStamp(
                            DELAY_PROFILE_TYPE,
                            profile.id.toString(),
                            profile,
                            DelayProfileResource.serializer(),
                        )
                    },
                    description = "Save delay profile",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    DELAY_PROFILE_TYPE,
                    saved.id.toString(),
                    saved,
                    DelayProfileResource.serializer(),
                )
            }
        }
    }

    suspend fun deleteDelayProfile(profileId: Int): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            deleteWithQueue(
                call = { delayProfileApi.removeDelayProfile(profileId) },
                type = DELAY_PROFILE_TYPE,
                id = profileId,
                path = "api/v3/delayprofile/$profileId",
                description = "Delete delay profile",
            )
        }

    /**
     * Moves a delay profile above its predecessor. Offline: the cached list
     * reorders immediately and the reorder PUT queues with the anchor
     * encoded in the query string.
     */
    suspend fun moveDelayProfile(profileId: Int, afterId: Int?): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val anchorPath = afterId?.let { "api/v3/delayprofile/reorder/$profileId?after=$it" }
                ?: "api/v3/delayprofile/reorder/$profileId"
            try {
                val saved = delayProfileApi.reorderDelayProfile(profileId, afterId)
                replace(DELAY_PROFILE_TYPE, saved, DelayProfileResource.serializer(), now())
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: IOException) {
                val cached = store.getAll(
                    serverId,
                    DELAY_PROFILE_TYPE,
                    DelayProfileResource.serializer(),
                )
                    .sortedBy { it.order }
                val reordered = reorderLocally(cached, profileId, afterId)
                    ?: return@withContext ArrResult.err(ArrError.Network(e))
                enqueueWrite(
                    method = PUT_METHOD,
                    path = anchorPath,
                    body = reordered,
                    serializer = ListSerializer(DelayProfileResource.serializer()),
                    cache = {
                        replace(
                            DELAY_PROFILE_TYPE,
                            reordered,
                            DelayProfileResource.serializer(),
                            stampPreserving(DELAY_PROFILE_TYPE),
                        )
                    },
                    description = "Reorder delay profiles",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                ArrResult.err(e.arrErrorOrUnknown())
            }
        }

    suspend fun saveReleaseProfile(
        profile: ReleaseProfileResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val isNew = profile.id <= 0
        if (isNew) {
            val response = try {
                releaseProfileApi.createReleaseProfile(profile)
            } catch (e: IOException) {
                return@withContext enqueueOptimisticCreate(
                    path = "api/v3/releaseprofile",
                    body = profile,
                    serializer = ReleaseProfileResource.serializer(),
                    type = RELEASE_PROFILE_TYPE,
                    description = "Create release profile",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    RELEASE_PROFILE_TYPE,
                    saved.id.toString(),
                    saved,
                    ReleaseProfileResource.serializer(),
                )
            }
        } else {
            val response = try {
                releaseProfileApi.updateReleaseProfile(profile.id.toString(), profile)
            } catch (e: IOException) {
                return@withContext enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/releaseprofile/${profile.id}",
                    body = profile,
                    serializer = ReleaseProfileResource.serializer(),
                    cache = {
                        putPreservingStamp(
                            RELEASE_PROFILE_TYPE,
                            profile.id.toString(),
                            profile,
                            ReleaseProfileResource.serializer(),
                        )
                    },
                    description = "Save release profile",
                    error = ArrError.Network(e),
                )
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrErrorOrUnknown())
            }
            applyWriteResponse(response) { saved ->
                store.put(
                    serverId,
                    RELEASE_PROFILE_TYPE,
                    saved.id.toString(),
                    saved,
                    ReleaseProfileResource.serializer(),
                )
            }
        }
    }

    suspend fun deleteReleaseProfile(profileId: Int): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            deleteWithQueue(
                call = { releaseProfileApi.removeReleaseProfile(profileId) },
                type = RELEASE_PROFILE_TYPE,
                id = profileId,
                path = "api/v3/releaseprofile/$profileId",
                description = "Delete release profile",
            )
        }

    /**
     * Rewrites one profile's score for a custom format and saves it. Used by
     * the custom format editor's score assignment rows; profiles that do not
     * list the format yet get it attached with the given score.
     */
    suspend fun assignFormatScore(
        profile: QualityProfileResource,
        formatId: Int,
        score: Int,
    ): ArrResult<WriteOutcome> {
        val existing = profile.formatItems.orEmpty()
        val updatedItems = existing.map { item ->
            if (item.format == formatId) item.copy(score = score) else item
        }.let { items ->
            if (existing.any { it.format == formatId }) {
                items
            } else {
                // New attachment goes to the end; the server reorders on save.
                items + ProfileFormatItemResource(format = formatId, score = score)
            }
        }
        return saveQualityProfile(profile.copy(formatItems = updatedItems))
    }

    /** Queued action count for this feature's writes, for the pending banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { pending ->
            pending.serverId == serverId &&
                FEATURE_PATHS.any { p -> pending.path.startsWith(p) }
        }
    }

    private suspend fun deleteWithQueue(
        call: suspend () -> Response<Unit>,
        type: EntityType,
        id: Int,
        path: String,
        description: String,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val response = try {
            call()
        } catch (e: IOException) {
            return@withContext enqueueWrite(
                method = DELETE_METHOD,
                path = path,
                body = null,
                serializer = Unit.serializer(),
                cache = { store.delete(serverId, type, id.toString()) },
                description = description,
                error = ArrError.Network(e),
            )
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrErrorOrUnknown())
        }
        if (response.isSuccessful) {
            store.delete(serverId, type, id.toString())
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
        val optimistic = withTempId(body, tempId) as T
        val action = action(
            method = POST_METHOD,
            path = path,
            bodyJson = ArrJson.encodeToString(serializer, body),
            description = description,
        )
        store.put(serverId, type, tempId.toString(), optimistic, serializer)
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Copies any resource row onto a temporary id by reflection-free known shapes. */
    private fun withTempId(body: Any?, tempId: Int): Any? = when (body) {
        is QualityProfileResource -> body.copy(id = tempId)
        is CustomFormatResource -> body.copy(id = tempId)
        is DelayProfileResource -> body.copy(id = tempId)
        is ReleaseProfileResource -> body.copy(id = tempId)
        else -> body
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
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$bodyJson".toByteArray(Charsets.UTF_8),
            ).toString()),
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

    /**
     * Rewrites a type's cache to exactly the given rows: upserts the fetched
     * set at [stampMillis] and deletes every other cached row so optimistic
     * edits and temporary creates reconcile on the next successful fetch.
     */
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

    /** Single-row upsert that keeps the existing fetch stamp, so optimistic writes do not
     *  extend the TTL. */
    private suspend fun <T> putPreservingStamp(
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
        is QualityDefinitionResource -> value.id.toString()
        is QualityProfileResource -> value.id.toString()
        is CustomFormatResource -> value.id.toString()
        is DelayProfileResource -> value.id.toString()
        is ReleaseProfileResource -> value.id.toString()
        is LanguageResource -> value.id.toString()
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
        internal val QUALITY_DEFINITION_TYPE = EntityType("radarr.qualitydefinition")
        internal val QUALITY_PROFILE_TYPE = EntityType("radarr.qualityprofile")
        internal val CUSTOM_FORMAT_TYPE = EntityType("radarr.customformat")
        internal val DELAY_PROFILE_TYPE = EntityType("radarr.delayprofile")
        internal val RELEASE_PROFILE_TYPE = EntityType("radarr.releaseprofile")
        internal val LANGUAGE_TYPE = EntityType("radarr.language")
        internal val LIMITS_TYPE = EntityType("radarr.qualitydefinition.limits")
        internal val PROFILE_SCHEMA_TYPE = EntityType("radarr.qualityprofile.schema")

        /** Five minutes; settings screens also re-read on pull-to-refresh. */
        const val QUALITY_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        /** Optimistic creates render under ids far below any server-assigned id. */
        internal const val TEMP_ID_BASE = -1_000_000_000L

        internal const val SINGLE_ID = "config"
        private const val PUT_METHOD = "PUT"
        private const val POST_METHOD = "POST"
        private const val DELETE_METHOD = "DELETE"

        internal val FEATURE_PATHS = setOf(
            "api/v3/qualitydefinition",
            "api/v3/qualityprofile",
            "api/v3/customformat",
            "api/v3/delayprofile",
            "api/v3/releaseprofile",
        )

        /**
         * Reorders a delay profile list the way the server endpoint does:
         * the moved row lands directly after [afterId], or at the top when
         * null. Returns null when the id is unknown.
         */
        internal fun reorderLocally(
            profiles: List<DelayProfileResource>,
            profileId: Int,
            afterId: Int?,
        ): List<DelayProfileResource>? {
            if (profiles.none { it.id == profileId }) return null
            val moving = profiles.first { it.id == profileId }
            val rest = profiles.filterNot { it.id == profileId }
            val insertAt = afterId?.let { anchor -> rest.indexOfFirst { it.id == anchor } + 1 } ?: 0
            val reordered = rest.subList(0, insertAt) + moving + rest.subList(insertAt, rest.size)
            return reordered.mapIndexed { index, profile -> profile.copy(order = index) }
        }
    }
}
