package dev.appolon.homelab.arr.feature.sonarr.detail.data

import dev.appolon.homelab.arr.api.sonarr.EpisodeApi
import dev.appolon.homelab.arr.api.sonarr.EpisodeFileApi
import dev.appolon.homelab.arr.api.sonarr.HistoryApi
import dev.appolon.homelab.arr.api.sonarr.ManualImportApi
import dev.appolon.homelab.arr.api.sonarr.ReleaseApi
import dev.appolon.homelab.arr.api.sonarr.RenameEpisodeApi
import dev.appolon.homelab.arr.api.sonarr.SeasonPassApi
import dev.appolon.homelab.arr.api.sonarr.SeriesApi
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodesMonitoredResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.sonarr.model.RenameEpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
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
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addAll
import retrofit2.HttpException

/** Command names the series detail screen dispatches. */
enum class DetailCommand(val wireName: String) {
    REFRESH_METADATA("RefreshSeries"),
    SCAN_DISK("RescanSeries"),
    SERIES_SEARCH("SeriesSearch"),
    SEASON_SEARCH("SeasonSearch"),
    EPISODES_SEARCH("EpisodesSearch"),
    RENAME_FILES("RenameFiles"),
}

/** Builds the JSON root for one command; selection ids live at the root. */
object SonarrCommandBodies {
    fun refreshSeries(seriesId: Int): String = root("name" to DetailCommand.REFRESH_METADATA.wireName) {
        put("seriesId", seriesId)
    }

    fun rescanSeries(seriesId: Int): String = root("name" to DetailCommand.SCAN_DISK.wireName) {
        put("seriesId", seriesId)
    }

    fun seriesSearch(seriesId: Int): String = root("name" to DetailCommand.SERIES_SEARCH.wireName) {
        put("seriesId", seriesId)
    }

    fun seasonSearch(seriesId: Int, seasonNumber: Int): String =
        root("name" to DetailCommand.SEASON_SEARCH.wireName) {
            put("seriesId", seriesId)
            put("seasonNumber", seasonNumber)
        }

    fun episodesSearch(episodeIds: List<Int>): String =
        root("name" to DetailCommand.EPISODES_SEARCH.wireName) {
            putJsonArray("episodeIds") { addAll(episodeIds) }
        }

    fun renameFiles(seriesId: Int): String = root("name" to DetailCommand.RENAME_FILES.wireName) {
        put("seriesId", seriesId)
    }

    private inline fun root(
        name: Pair<String, String>,
        build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): String = buildJsonObject {
        put(name.first, name.second)
        build()
    }.toString()
}

/**
 * Posts one raw command body to the command endpoint. Abstract so tests and
 * previews substitute a lambda; the production implementation rides the
 * shared OkHttp client (see the connection file).
 */
fun interface SonarrCommandPoster {
    suspend fun post(bodyJson: String): ArrResult<Unit>
}

/**
 * Series detail repository. Reads are cache-first through the [EntityStore]
 * with a fetchedAt TTL; writes hit the server first and, when the network
 * fails, apply optimistically to the cache and enqueue for replay through
 * the [ActionQueue]. The series entity type value matches the library's so
 * both screens share one cache. No function throws.
 */
class SonarrSeriesDetailRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val seriesApi: SeriesApi,
    private val episodeApi: EpisodeApi,
    private val episodeFileApi: EpisodeFileApi,
    private val renameEpisodeApi: RenameEpisodeApi,
    private val seasonPassApi: SeasonPassApi,
    private val historyApi: HistoryApi,
    private val releaseApi: ReleaseApi,
    private val manualImportApi: ManualImportApi,
    private val commandPoster: SonarrCommandPoster,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val qualityDefinitionApi: suspend () -> List<QualityDefinitionResource>,
    private val languageApi: suspend () -> List<LanguageResource>,
    private val tagApi: suspend () -> List<TagResource>,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Live series row; emits again after every write-through. */
    fun observeSeries(seriesId: Int): Flow<SeriesResource?> =
        store.observe(serverId, SERIES_TYPE, SeriesResource.serializer())
            .map { rows -> rows.firstOrNull { it.id == seriesId } }

    suspend fun cachedSeries(seriesId: Int): SeriesResource? =
        store.get(serverId, SERIES_TYPE, seriesId.toString(), SeriesResource.serializer())

    /** True when the shared series cache is absent or older than [ttlMillis]. */
    suspend fun seriesStale(ttlMillis: Long = SERIES_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, SERIES_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches one series and writes it through. */
    suspend fun refreshSeries(seriesId: Int): ArrResult<SeriesResource> =
        withContext(ioDispatcher) {
            val fetched = try {
                seriesApi.get(seriesId)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            store.put(serverId, SERIES_TYPE, seriesId.toString(), fetched, SERIES_SER)
            ArrResult.ok(fetched)
        }

    /** All cached episodes of one series. */
    fun observeEpisodes(seriesId: Int): Flow<List<EpisodeResource>> =
        store.observe(serverId, EPISODE_TYPE, EpisodeResource.serializer())
            .map { rows -> rows.filter { it.seriesId == seriesId } }

    /**
     * Fetches every episode of the series with its file embedded, then
     * reconciles this series' cache rows (removes rows the server dropped).
     */
    suspend fun refreshEpisodes(seriesId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            episodeApi.list(seriesId = seriesId, includeEpisodeFile = true)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        reconcile(EPISODE_TYPE, fetched, cachedEpisodesOf(seriesId)) { it.id.toString() }
        store.putAll(
            serverId,
            EPISODE_TYPE,
            fetched.map { it.id.toString() to it },
            EPISODE_SER,
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** History events of one series, newest first. */
    fun observeHistory(seriesId: Int): Flow<List<HistoryResource>> =
        store.observe(serverId, HISTORY_TYPE, HistoryResource.serializer())
            .map { rows ->
                rows.filter { it.seriesId == seriesId }
                    .sortedByDescending { it.date }
            }

    /** Fetches the series history feed and reconciles its cached rows. */
    suspend fun refreshHistory(seriesId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            historyApi.series(seriesId = seriesId, includeEpisode = true)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        reconcile(HISTORY_TYPE, fetched, cachedHistoryOf(seriesId)) { it.id.toString() }
        store.putAll(
            serverId,
            HISTORY_TYPE,
            fetched.map { it.id.toString() to it },
            HISTORY_SER,
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Cache-first lookup family, each with its own TTL. */
    suspend fun qualityProfiles(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        PROFILE_TYPE,
        QualityProfileResource.serializer(),
        { it.id.toString() },
        qualityProfileApi,
        ttlMillis,
    )

    suspend fun qualityDefinitions(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        QUALITY_TYPE,
        QualityDefinitionResource.serializer(),
        { it.id.toString() },
        qualityDefinitionApi,
        ttlMillis,
    )

    suspend fun languages(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        LANGUAGE_TYPE,
        LanguageResource.serializer(),
        { it.id.toString() },
        languageApi,
        ttlMillis,
    )

    suspend fun tags(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        TAG_TYPE,
        TagResource.serializer(),
        { it.id.toString() },
        tagApi,
        ttlMillis,
    )

    private suspend fun <T> lookup(
        type: EntityType,
        serializer: kotlinx.serialization.KSerializer<T>,
        idOf: (T) -> String,
        fetch: suspend () -> List<T>,
        ttlMillis: Long,
    ): ArrResult<List<T>> = withContext(ioDispatcher) {
        val cached = store.getAll(serverId, type, serializer)
        val fetchedAt = store.lastFetchedAt(serverId, type)
        if (fetchedAt != null && now() - fetchedAt <= ttlMillis && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val remote = try {
            fetch()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        if (remote.isNotEmpty()) {
            store.putAll(
                serverId,
                type,
                remote.map { idOf(it) to it },
                serializer,
                fetchedAtMillis = now(),
            )
            ArrResult.ok(remote)
        } else {
            ArrResult.ok(cached)
        }
    }

    /**
     * Toggles one season's monitored flag through the series update
     * endpoint. The response is authoritative and written through; offline,
     * the merged resource lands in the cache and the PUT is queued.
     */
    suspend fun setSeasonMonitored(
        series: SeriesResource,
        seasonNumber: Int,
        monitored: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val updated = series.withSeasonMonitored(seasonNumber, monitored)
        updateSeriesResource(updated, moveFiles = false)
    }

    /** Series-level monitored toggle, same path and guarantees as the season toggle. */
    suspend fun setSeriesMonitored(
        series: SeriesResource,
        monitored: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        updateSeriesResource(series.copy(monitored = monitored), moveFiles = false)
    }

    /**
     * Full series edit from the edit sheet. `moveFiles` asks the server to
     * relocate files after a path change.
     */
    suspend fun updateSeries(
        series: SeriesResource,
        moveFiles: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        updateSeriesResource(series, moveFiles)
    }

    private suspend fun updateSeriesResource(
        updated: SeriesResource,
        moveFiles: Boolean,
    ): ArrResult<WriteOutcome> = try {
        val path = "api/v3/series/${updated.id}"
        val saved = seriesApi.update(updated.id.toString(), moveFiles, updated)
        store.put(serverId, SERIES_TYPE, saved.id.toString(), saved, SERIES_SER)
        ArrResult.ok(WriteOutcome.Synced)
    } catch (e: HttpException) {
        enqueueOrErr(
            e.toArrError(),
            "PUT",
            "api/v3/series/${updated.id}",
            ArrJson.encodeToString(SERIES_SER, updated),
        ) {
            store.put(serverId, SERIES_TYPE, updated.id.toString(), updated, SERIES_SER)
        }
    } catch (e: Throwable) {
        enqueueOrErr(
            e.arrError(),
            "PUT",
            "api/v3/series/${updated.id}",
            ArrJson.encodeToString(SERIES_SER, updated),
        ) {
            store.put(serverId, SERIES_TYPE, updated.id.toString(), updated, SERIES_SER)
        }
    }

    /**
     * Toggles monitoring for a set of episodes. The endpoint returns no body,
     * so the cache rows flip optimistically on both the synced and queued
     * paths.
     */
    suspend fun setEpisodesMonitored(
        seriesId: Int,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        if (episodeIds.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val body = EpisodesMonitoredResource(episodeIds = episodeIds, monitored = monitored)
        val path = "api/v3/episode/monitor"
        try {
            episodeApi.updateMonitor(includeImages = false, body = body)
            applyEpisodeMonitored(episodeIds, monitored)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "PUT", path, ArrJson.encodeToString(MONITOR_SER, body)) {
                applyEpisodeMonitored(episodeIds, monitored)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "PUT", path, ArrJson.encodeToString(MONITOR_SER, body)) {
                applyEpisodeMonitored(episodeIds, monitored)
            }
        }
    }

    /**
     * Saves the episode file editor's quality, language, and release group
     * choices. Both paths update the embedded file on cached episodes.
     */
    suspend fun updateEpisodeFile(file: EpisodeFileResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val path = "api/v3/episodefile/${file.id}"
            try {
                val saved = episodeFileApi.update(file.id.toString(), file)
                applyEpisodeFile(saved)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), "PUT", path, ArrJson.encodeToString(FILE_SER, file)) {
                    applyEpisodeFile(file)
                }
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), "PUT", path, ArrJson.encodeToString(FILE_SER, file)) {
                    applyEpisodeFile(file)
                }
            }
        }

    /**
     * Deletes one episode file. Both paths detach the file from cached
     * episodes so the row shows missing immediately.
     */
    suspend fun deleteEpisodeFile(fileId: Int): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "api/v3/episodefile/$fileId"
        try {
            episodeFileApi.delete(fileId)
            applyEpisodeFileRemoved(fileId)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "DELETE", path, null) {
                applyEpisodeFileRemoved(fileId)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "DELETE", path, null) {
                applyEpisodeFileRemoved(fileId)
            }
        }
    }

    /**
     * Season pass for this one series: per-series monitoring plus the
     * monitor strategy the server applies to recompute episode flags.
     */
    suspend fun applySeasonPass(pass: SeasonPassResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val path = "api/v3/seasonpass"
            try {
                seasonPassApi.create(pass)
                applyPassLocally(pass)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), "POST", path, ArrJson.encodeToString(PASS_SER, pass)) {
                    applyPassLocally(pass)
                }
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), "POST", path, ArrJson.encodeToString(PASS_SER, pass)) {
                    applyPassLocally(pass)
                }
            }
        }

    /** Delete with the two web UI flags; both paths clear this series' cache rows. */
    suspend fun deleteSeries(
        seriesId: Int,
        deleteFiles: Boolean,
        addImportListExclusion: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "api/v3/series/$seriesId?deleteFiles=$deleteFiles" +
            "&addImportListExclusion=$addImportListExclusion"
        try {
            seriesApi.delete(seriesId, deleteFiles, addImportListExclusion)
            purgeSeriesCache(seriesId)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "DELETE", path, null) { purgeSeriesCache(seriesId) }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "DELETE", path, null) { purgeSeriesCache(seriesId) }
        }
    }

    /** Rename preview rows; a transient server computation, never cached. */
    suspend fun renamePreview(seriesId: Int): ArrResult<List<RenameEpisodeResource>> =
        withContext(ioDispatcher) {
            try {
                ArrResult.ok(renameEpisodeApi.list(seriesId = seriesId))
            } catch (e: HttpException) {
                ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                ArrResult.err(e.arrError())
            }
        }

    /** Sends a command; offline commands queue and replay unchanged. */
    suspend fun runCommandBody(bodyJson: String, description: String): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val failure = try {
                val result = commandPoster.post(bodyJson)
                if (result is ArrResult.Err) result.error else null
            } catch (e: Throwable) {
                e.arrError()
            }
            if (failure == null) {
                ArrResult.ok(WriteOutcome.Synced)
            } else if (failure.isRetryable()) {
                enqueueAction("POST", COMMAND_PATH, bodyJson, description)?.let {
                    ArrResult.ok(WriteOutcome.Queued(it))
                } ?: ArrResult.err(failure)
            } else {
                ArrResult.err(failure)
            }
        }

    /** Manual search releases for an episode or a whole season. */
    suspend fun releases(
        seriesId: Int,
        episodeId: Int?,
        seasonNumber: Int?,
    ): ArrResult<List<ReleaseResource>> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(releaseApi.list(seriesId = seriesId, episodeId = episodeId, seasonNumber = seasonNumber))
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Grabs one release; offline grabs queue. */
    suspend fun grabRelease(release: ReleaseResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val path = "api/v3/release"
            try {
                releaseApi.create(release)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), "POST", path, ArrJson.encodeToString(RELEASE_SER, release))
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), "POST", path, ArrJson.encodeToString(RELEASE_SER, release))
            }
        }

    /** Interactive import candidates under the series folder. */
    suspend fun importCandidates(
        folder: String?,
        seriesId: Int,
        seasonNumber: Int?,
    ): ArrResult<List<ManualImportResource>> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(
                manualImportApi.list(
                    folder = folder,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                ),
            )
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Imports the chosen candidates; offline imports queue. */
    suspend fun importItems(items: List<ManualImportReprocessResource>): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            if (items.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
            val path = "api/v3/manualimport"
            val body = ArrJson.encodeToString(ListSerializer(MANUAL_SER), items)
            try {
                manualImportApi.create(items)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), "POST", path, body)
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), "POST", path, body)
            }
        }

    /** Series ids touched by this screen's queued actions, for the pending badge. */
    fun queuedSeriesIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId }
            .flatMap(::seriesIdsOf)
            .toSet()
    }

    /** Count of every queued action for this server. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    private fun seriesIdsOf(action: QueuedAction): Sequence<Int> = sequence {
        when {
            action.path.startsWith("api/v3/series/") && !action.path.startsWith("api/v3/series/editor") ->
                yield(action.path.substringAfter("api/v3/series/").substringBefore('?').toIntOrNull() ?: return@sequence)

            action.path == "api/v3/seasonpass" ->
                runCatching { ArrJson.decodeFromString(PASS_SER, action.bodyJson.orEmpty()) }
                    .getOrNull()
                    ?.let { pass -> yieldAll(pass.series.orEmpty().map { it.id }) }

            action.path == "api/v3/command" -> {
                val root = runCatching {
                    ArrJson.parseToJsonElement(action.bodyJson.orEmpty()) as? JsonObject
                }.getOrNull()
                yieldAll(listOfNotNull(root?.get("seriesId")?.jsonPrimitive?.int))
            }

            action.path == "api/v3/release" ->
                runCatching { ArrJson.decodeFromString(RELEASE_SER, action.bodyJson.orEmpty()) }
                    .getOrNull()
                    ?.let { release -> yieldAll(listOfNotNull(release.seriesId)) }

            action.path == "api/v3/manualimport" ->
                runCatching {
                    ArrJson.decodeFromString(ListSerializer(MANUAL_SER), action.bodyJson.orEmpty())
                }.getOrNull()
                    ?.let { rows -> yieldAll(rows.map { it.seriesId }.distinct()) }
        }
    }

    private suspend fun cachedEpisodesOf(seriesId: Int): List<EpisodeResource> =
        store.getAll(serverId, EPISODE_TYPE, EPISODE_SER).filter { it.seriesId == seriesId }

    private suspend fun cachedHistoryOf(seriesId: Int): List<HistoryResource> =
        store.getAll(serverId, HISTORY_TYPE, HISTORY_SER).filter { it.seriesId == seriesId }

    /** Removes rows of this collection that disappeared server-side. */
    private suspend fun <T> reconcile(
        type: EntityType,
        incoming: List<T>,
        existing: List<T>,
        idOf: (T) -> String,
    ) {
        val incomingIds = incoming.map(idOf).toSet()
        existing.forEach { row ->
            val id = idOf(row)
            if (id !in incomingIds) store.delete(serverId, type, id)
        }
    }

    private suspend fun applyEpisodeMonitored(episodeIds: List<Int>, monitored: Boolean) {
        val targets = episodeIds.toSet()
        cachedEpisodesAll().filter { it.id in targets }.forEach { episode ->
            store.put(
                serverId,
                EPISODE_TYPE,
                episode.id.toString(),
                episode.copy(monitored = monitored),
                EPISODE_SER,
            )
        }
    }

    private suspend fun applyEpisodeFile(file: EpisodeFileResource) {
        cachedEpisodesAll().filter { it.episodeFileId == file.id }.forEach { episode ->
            store.put(
                serverId,
                EPISODE_TYPE,
                episode.id.toString(),
                episode.copy(episodeFile = file),
                EPISODE_SER,
            )
        }
    }

    private suspend fun applyEpisodeFileRemoved(fileId: Int) {
        cachedEpisodesAll().filter { it.episodeFileId == fileId }.forEach { episode ->
            store.put(
                serverId,
                EPISODE_TYPE,
                episode.id.toString(),
                episode.copy(
                    hasFile = false,
                    episodeFileId = 0,
                    episodeFile = EpisodeFileResource(),
                ),
                EPISODE_SER,
            )
        }
    }

    private suspend fun applyPassLocally(pass: SeasonPassResource) {
        pass.series.orEmpty().forEach { entry ->
            val cached = cachedSeries(entry.id) ?: return@forEach
            val merged = cached.copy(
                monitored = entry.monitored ?: cached.monitored,
                seasons = entry.seasons ?: cached.seasons,
            )
            store.put(serverId, SERIES_TYPE, entry.id.toString(), merged, SERIES_SER)
        }
    }

    private suspend fun purgeSeriesCache(seriesId: Int) {
        store.delete(serverId, SERIES_TYPE, seriesId.toString())
        cachedEpisodesOf(seriesId).forEach { store.delete(serverId, EPISODE_TYPE, it.id.toString()) }
        cachedHistoryOf(seriesId).forEach { store.delete(serverId, HISTORY_TYPE, it.id.toString()) }
    }

    private suspend fun cachedEpisodesAll(): List<EpisodeResource> =
        store.getAll(serverId, EPISODE_TYPE, EPISODE_SER)

    private suspend fun enqueueOrErr(
        error: ArrError,
        method: String,
        path: String,
        bodyJson: String?,
        optimistic: (suspend () -> Unit)? = null,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        optimistic?.invoke()
        val actionId = enqueueAction(method, path, bodyJson, describe(method, path))
            ?: return ArrResult.err(error)
        return ArrResult.ok(WriteOutcome.Queued(actionId))
    }

    private suspend fun enqueueAction(
        method: String,
        path: String,
        bodyJson: String?,
        description: String,
    ): ActionId? = try {
        val action = QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$bodyJson".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.SONARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )
        actionQueue.enqueue(action)
        action.actionId
    } catch (e: Throwable) {
        null
    }

    private fun describe(method: String, path: String): String = when {
        path.startsWith("api/v3/series/") && method == "DELETE" -> "Delete series"
        path.startsWith("api/v3/series/") -> "Edit series"
        path == "api/v3/episode/monitor" -> "Episode monitoring"
        path.startsWith("api/v3/episodefile/") && method == "DELETE" -> "Delete episode file"
        path.startsWith("api/v3/episodefile/") -> "Edit episode file"
        path == "api/v3/seasonpass" -> "Season pass"
        path == "api/v3/release" -> "Grab release"
        path == "api/v3/manualimport" -> "Interactive import"
        else -> "Sonarr write"
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    private companion object {
        /** Same value as the library's type, so both screens share the cache. */
        internal val SERIES_TYPE = EntityType("sonarr.series")
        internal val EPISODE_TYPE = EntityType("sonarr.episode")
        internal val HISTORY_TYPE = EntityType("sonarr.history")
        internal val PROFILE_TYPE = EntityType("sonarr.qualityprofile")
        internal val QUALITY_TYPE = EntityType("sonarr.qualitydefinition")
        internal val LANGUAGE_TYPE = EntityType("sonarr.language")
        internal val TAG_TYPE = EntityType("sonarr.tag")
        internal const val COMMAND_PATH = "api/v3/command"

        private val SERIES_SER = SeriesResource.serializer()
        private val EPISODE_SER = EpisodeResource.serializer()
        private val HISTORY_SER = HistoryResource.serializer()
        private val MONITOR_SER = EpisodesMonitoredResource.serializer()
        private val FILE_SER = EpisodeFileResource.serializer()
        private val PASS_SER = SeasonPassResource.serializer()
        private val RELEASE_SER = ReleaseResource.serializer()
        private val MANUAL_SER = ManualImportReprocessResource.serializer()

        /** Ten minutes, matching the library's series TTL. */
        const val SERIES_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY

        /** One hour; profiles, qualities, languages, and tags change rarely. */
        const val LOOKUP_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS
    }
}

/** Copies the series with one season's monitored flag flipped. */
internal fun SeriesResource.withSeasonMonitored(
    seasonNumber: Int,
    monitored: Boolean,
): SeriesResource = copy(
    seasons = seasons.orEmpty().map { season ->
        if (season.seasonNumber == seasonNumber) season.copy(monitored = monitored) else season
    },
)
