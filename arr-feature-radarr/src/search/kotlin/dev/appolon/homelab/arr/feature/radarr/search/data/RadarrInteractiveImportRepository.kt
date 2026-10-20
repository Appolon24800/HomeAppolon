package dev.appolon.homelab.arr.feature.radarr.search.data

import dev.appolon.homelab.arr.api.radarr.ManualImportApi
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
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
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * The movie-scoped interactive import decision flow: browse folders, list
 * candidate files, and import each selection with the quality and language
 * the user picked per file. Distinct from the queue group's manual import,
 * which serves queue-scoped entries; this repository always forces the flow's
 * movie id so every imported file lands on the movie the screen was opened
 * for. Listing is inherently online (live folders); the import write falls
 * back to the [ActionQueue] when offline. Nothing throws.
 */
class RadarrInteractiveImportRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val manualImportApi: ManualImportApi,
    private val fileSystemCall: suspend (path: String?, includeFiles: Boolean) -> FileSystemResult,
    private val movieById: suspend (Int) -> MovieResource?,
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
            val result = try {
                fileSystemCall(path, false)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: IOException) {
                return@withContext ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            val parent = result.parent?.takeIf { it.isNotEmpty() }
            val folders = result.directories.orEmpty()
                .filter {
                    it.type == FileSystemEntityType.FOLDER || it.type == FileSystemEntityType.DRIVE
                }
                .mapNotNull { model ->
                    model.path?.let { ImportFolder(path = it, name = model.name ?: it) }
                }
            ArrResult.ok(
                if (parent == null) {
                    folders
                } else {
                    listOf(ImportFolder(parent, PARENT_LABEL, isParent = true)) + folders
                },
            )
        }

    /** Import candidates parsed from one folder, defaulted to the flow's movie. */
    suspend fun listFiles(folder: String): ArrResult<List<ManualImportResource>> =
        withContext(io) {
            try {
                ArrResult.ok(
                    manualImportApi.manualImport(folder = folder, filterExistingFiles = true),
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
     * The movie the flow imports into, cache-first: reads the shared movie
     * cache the library group fills, falling back to a direct fetch that
     * writes through so later screens reuse it.
     */
    suspend fun movie(movieId: Int): ArrResult<MovieResource> = withContext(io) {
        val cached = store.get(serverId, MOVIE_TYPE, movieId.toString(), MovieResource.serializer())
        if (cached != null && cached.id == movieId) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            movieById(movieId)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        } ?: return@withContext ArrResult.err(ArrError.Http(404, null))
        store.put(serverId, MOVIE_TYPE, movieId.toString(), fetched, MovieResource.serializer())
        ArrResult.ok(fetched)
    }

    /**
     * Selectable qualities of the movie's profile, flattened out of groups,
     * in profile order. Empty when the profile cannot be loaded; the file's
     * parsed quality then remains the only choice.
     */
    suspend fun qualityOptions(movie: MovieResource): List<Quality> = withContext(io) {
        val profile = try {
            qualityProfileById(movie.qualityProfileId)
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
     * language (falling back to what Radarr parsed). Offline the POST is
     * enqueued for replay.
     */
    suspend fun importSelections(
        movie: MovieResource,
        decisions: List<ImportDecision>,
    ): ArrResult<WriteOutcome> = withContext(io) {
        if (decisions.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val body = decisions.map { decision -> toReprocess(movie, decision) }
        val failure = try {
            manualImportApi.reprocessManualImport(body)
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
                val actionId = ActionId(UUID.nameUUIDFromBytes(
                    "$serverId:POST:$IMPORT_PATH:$body".toByteArray(Charsets.UTF_8),
                ).toString())
                actionQueue.enqueue(
                    QueuedAction(
                        actionId = actionId,
                        serverId = serverId,
                        appType = AppType.RADARR,
                        method = "POST",
                        path = IMPORT_PATH,
                        bodyJson = json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(
                                ManualImportReprocessResource.serializer(),
                            ),
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
        movie: MovieResource,
        decision: ImportDecision,
    ): ManualImportReprocessResource {
        val resource = decision.resource
        val quality = if (decision.quality != null) {
            QualityModel(quality = decision.quality, revision = resource.quality.revision)
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
            movieId = movie.id,
            movie = movie,
            quality = quality,
            languages = languages,
            releaseGroup = resource.releaseGroup,
            downloadId = resource.downloadId,
            customFormats = resource.customFormats,
            customFormatScore = resource.customFormatScore,
            indexerFlags = resource.indexerFlags,
            rejections = resource.rejections,
        )
    }

    private fun flattenQualityItems(
        item: QualityProfileQualityItemResource,
    ): List<Quality> = when {
        // listOfNotNull because the api module's public val does not smart
        // cast to non-null across the module boundary.
        item.quality != null -> if (item.allowed) listOfNotNull(item.quality) else emptyList()
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

        /**
         * Same type value as the library group's movie cache so both groups
         * share rows; it is restated here because that constant is internal
         * to this package family.
         */
        internal val MOVIE_TYPE = EntityType("radarr.movie")
    }
}
