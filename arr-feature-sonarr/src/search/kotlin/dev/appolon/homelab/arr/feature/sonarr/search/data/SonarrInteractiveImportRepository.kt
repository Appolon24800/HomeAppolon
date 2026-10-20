package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.ManualImportApi
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
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
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/** One browsable folder row: a directory path or the parent hop. */
data class ImportFolder(
    val path: String,
    val name: String,
    val isParent: Boolean = false,
)

/** A file row plus the per-file decisions the user can still change. */
data class ImportDecision(
    val resource: ManualImportResource,
    val quality: Quality?,
    val language: Language?,
    val selected: Boolean,
)

/** Matched series title of an import candidate; null when unmatched. */
fun ManualImportResource.seriesTitleOrNull(): String? = series.stringOf("title")

/**
 * The series-scoped interactive import decision flow: browse folders, list
 * candidate files, and import each selection with the quality and language
 * the user picked per file. Distinct from the queue group's manual import,
 * which serves queue-scoped downloads; this repository always pins the
 * flow's series id (and season when the flow started from one) so every
 * imported file lands where the screen was opened for. Listing is inherently
 * online (live folders); the import write falls back to the [ActionQueue]
 * when offline. Nothing throws.
 */
class SonarrInteractiveImportRepository(
    private val seasonNumber: Int? = null,
    private val serverId: ServerId,
    private val store: EntityStore,
    private val manualImportApi: ManualImportApi,
    private val fileSystemCall: suspend (path: String?, includeFiles: Boolean) -> JsonObject,
    private val seriesById: suspend (Int) -> SeriesResource?,
    private val languageTable: suspend () -> List<LanguageResource>,
    private val qualityProfileById: suspend (Int) -> QualityProfileResource?,
    private val actionQueue: ActionQueue,
    private val json: kotlinx.serialization.json.Json = ArrJson,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Folders inside [path]; null lists the server's root view. */
    suspend fun listFolders(path: String?): ArrResult<List<ImportFolder>> =
        withContext(io) {
            val listing = try {
                fileSystemCall(path, false)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: IOException) {
                return@withContext ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            val parent = listing.stringOf("parent")
            val folders = (listing["directories"] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                .orEmpty()
                .filter { dir -> dir.stringOf("type") in FOLDER_TYPES }
                .mapNotNull { dir ->
                    dir.stringOf("path")?.let { path ->
                        ImportFolder(path = path, name = dir.stringOf("name") ?: path)
                    }
                }
            ArrResult.ok(
                if (parent == null) {
                    folders
                } else {
                    listOf(ImportFolder(parent, PARENT_LABEL, isParent = true)) + folders
                },
            )
        }

    /**
     * Import candidates of one folder. The call narrows to the flow's season
     * but not its series: files Sonarr matched to a different series stay
     * listed so the user can still import them under this flow's series,
     * which is exactly the decision the reprocess body pins.
     */
    suspend fun listFiles(folder: String): ArrResult<List<ManualImportResource>> =
        withContext(io) {
            try {
                ArrResult.ok(
                    manualImportApi.list(
                        folder = folder,
                        seasonNumber = seasonNumber,
                        filterExistingFiles = true,
                    ),
                )
            } catch (e: HttpException) {
                ArrResult.err(e.toArrError())
            } catch (e: IOException) {
                ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                ArrResult.err(e.arrError())
            }
        }

    /**
     * The series the flow imports into, cache-first: reads the shared series
     * cache the library group fills (same entity type value), falling back to
     * a direct fetch that writes through so later screens reuse it.
     */
    suspend fun series(id: Int): ArrResult<SeriesResource> = withContext(io) {
        val cached = store.get(serverId, SERIES_TYPE, id.toString(), SeriesResource.serializer())
        if (cached != null && cached.id == id) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            seriesById(id)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        } ?: return@withContext ArrResult.err(ArrError.Http(404, null))
        store.put(serverId, SERIES_TYPE, id.toString(), fetched, SeriesResource.serializer())
        ArrResult.ok(fetched)
    }

    /**
     * Selectable qualities of the series' profile, flattened out of groups,
     * in profile order. Empty when the profile cannot be loaded; the file's
     * parsed quality then remains the only choice.
     */
    suspend fun qualityOptions(series: SeriesResource): List<Quality> = withContext(io) {
        val profile = try {
            qualityProfileById(series.qualityProfileId)
        } catch (_: Throwable) {
            null
        }
        profile?.items.orEmpty().flatMap(::flattenQualityItems)
    }

    /** The server language table, by name, for the per-file language pick. */
    suspend fun languageOptions(): List<Language> = withContext(io) {
        val table = try {
            languageTable()
        } catch (_: Throwable) {
            emptyList()
        }
        table
            .filter { it.id > 0 && !it.name.isNullOrBlank() }
            .map { Language(id = it.id, name = it.name) }
            .sortedBy { it.name.orEmpty() }
    }

    /**
     * Imports the approved decisions, posting each file's chosen quality and
     * language (falling back to what Sonarr parsed) with the flow's series
     * pinned. Offline the POST is enqueued for replay.
     */
    suspend fun importSelections(
        series: SeriesResource,
        decisions: List<ImportDecision>,
    ): ArrResult<WriteOutcome> = withContext(io) {
        if (decisions.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val body = decisions.map { decision -> toReprocess(series, decision) }
        val failure = try {
            manualImportApi.create(body)
            null
        } catch (e: HttpException) {
            e.toArrError()
        } catch (e: IOException) {
            ArrError.Network(e)
        } catch (e: Throwable) {
            e.arrError()
        }
        when {
            failure == null -> ArrResult.ok(WriteOutcome.Synced)

            failure is ArrError.Offline || failure is ArrError.Network -> {
                val actionId = ActionId(UUID.randomUUID().toString())
                actionQueue.enqueue(
                    QueuedAction(
                        actionId = actionId,
                        serverId = serverId,
                        appType = AppType.SONARR,
                        method = "POST",
                        path = IMPORT_PATH,
                        bodyJson = json.encodeToString(
                            ListSerializer(ManualImportReprocessResource.serializer()),
                            body,
                        ),
                        description = "Interactive import ${body.size} files",
                        enqueuedAtMillis = now(),
                    ),
                )
                ArrResult.ok(WriteOutcome.Queued(actionId))
            }

            else -> ArrResult.err(failure)
        }
    }

    private fun toReprocess(
        series: SeriesResource,
        decision: ImportDecision,
    ): ManualImportReprocessResource {
        val resource = decision.resource
        val quality = if (decision.quality != null) {
            resource.quality.copy(quality = decision.quality)
        } else {
            resource.quality
        }
        val languages = if (decision.language != null) {
            listOf(decision.language)
        } else {
            resource.languages
        }
        return ManualImportReprocessResource(
            id = resource.id,
            path = resource.path,
            seriesId = series.id,
            seasonNumber = resource.seasonNumber,
            episodes = resource.episodes,
            episodeIds = resource.episodes.orEmpty().mapNotNull { it.intOf("id") },
            quality = quality,
            languages = languages,
            releaseGroup = resource.releaseGroup,
            downloadId = resource.downloadId,
            customFormats = resource.customFormats,
            customFormatScore = resource.customFormatScore,
            indexerFlags = resource.indexerFlags,
            releaseType = resource.releaseType,
            rejections = resource.rejections,
        )
    }

    private fun flattenQualityItems(
        item: QualityProfileQualityItemResource,
    ): List<Quality> = when {
        // The api module's public val does not smart cast to non-null across
        // the module boundary, so descend groups structurally instead.
        item.quality.id != 0 || item.quality.name != null ->
            if (item.allowed) listOf(item.quality) else emptyList()

        // Groups carry their enabled state on the children; the container's
        // own `allowed` is not meaningful, so always descend.
        else -> item.items.orEmpty().flatMap(::flattenQualityItems)
    }

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal const val IMPORT_PATH = "api/v3/manualimport"
        internal const val PARENT_LABEL = ".."

        /** The filesystem endpoint's directory discriminators. */
        private val FOLDER_TYPES = setOf("folder", "drive")

        /**
         * Same type value as the library group's series cache so both groups
         * share rows; restated here because that constant lives in another
         * package family.
         */
        internal val SERIES_TYPE = EntityType("sonarr.series")
    }
}

private fun JsonObject.stringOf(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

internal fun JsonObject.intOf(key: String): Int? = when (val element = this[key]) {
    is JsonPrimitive -> element.content.toIntOrNull()
    else -> null
}
