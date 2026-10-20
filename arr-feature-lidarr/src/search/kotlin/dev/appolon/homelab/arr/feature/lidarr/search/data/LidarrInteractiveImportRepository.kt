package dev.appolon.homelab.arr.feature.lidarr.search.data

import dev.appolon.homelab.arr.api.lidarr.ManualImportApi
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
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

/** A file row plus the per-file quality decision the user can still change. */
data class ImportDecision(
    val resource: ManualImportResource,
    val quality: Quality?,
    val selected: Boolean,
)

/** True when Lidarr resolved the artist, album, and release a candidate belongs to. */
fun ManualImportResource.hasCompleteMatch(): Boolean =
    (artist?.id ?: 0) > 0 && (album?.id ?: 0) > 0 && albumReleaseId > 0

/** Matched artist name of an import candidate; null when unmatched. */
fun ManualImportResource.artistTitleOrNull(): String? =
    artist?.artistName?.takeIf { it.isNotEmpty() }

/** Matched album title of an import candidate; null when unmatched. */
fun ManualImportResource.albumTitleOrNull(): String? =
    album?.title?.takeIf { it.isNotEmpty() }

/**
 * The artist-scoped interactive import decision flow: browse folders, list
 * candidate files, and import each selection with the quality the user
 * picked per file. Distinct from the queue group's manual import, which
 * serves queue-scoped downloads; this flow starts in the artist's own folder
 * and every imported file keeps the artist, album, release, and tracks
 * Lidarr matched for it, so the decision the user makes here is the quality.
 * Listing is inherently online (live folders); the import write falls back
 * to the [ActionQueue] when offline. Nothing throws.
 */
class LidarrInteractiveImportRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val manualImportApi: ManualImportApi,
    private val fileSystemCall: suspend (path: String?, includeFiles: Boolean) -> JsonObject,
    private val artistById: suspend (Int) -> ArtistResource?,
    private val albumById: suspend (Int) -> AlbumResource?,
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
     * Import candidates of one folder. The call narrows to the folder but not
     * the flow's artist: files Lidarr matched to a different artist stay
     * listed so the user can see what the parse found; only complete matches
     * are selectable, because the import body replays each file's own match.
     */
    suspend fun listFiles(folder: String): ArrResult<List<ManualImportResource>> =
        withContext(io) {
            try {
                ArrResult.ok(
                    manualImportApi.getManualImport(
                        folder = folder,
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
     * The artist the flow imports into, cache-first: reads the shared artist
     * cache the library group fills (same entity type value), falling back to
     * a direct fetch that writes through so later screens reuse it.
     */
    suspend fun artist(id: Int): ArrResult<ArtistResource> = withContext(io) {
        val cached = store.get(serverId, ARTIST_TYPE, id.toString(), ArtistResource.serializer())
        if (cached != null && cached.id == id) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            artistById(id)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        } ?: return@withContext ArrResult.err(ArrError.Http(404, null))
        store.put(serverId, ARTIST_TYPE, id.toString(), fetched, ArtistResource.serializer())
        ArrResult.ok(fetched)
    }

    /**
     * One album, cache-first over the shared album cache; the album scope's
     * grab options resolve the artist through it.
     */
    suspend fun album(id: Int): ArrResult<AlbumResource> = withContext(io) {
        val cached = store.get(serverId, ALBUM_TYPE, id.toString(), AlbumResource.serializer())
        if (cached != null && cached.id == id) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            albumById(id)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        } ?: return@withContext ArrResult.err(ArrError.Http(404, null))
        store.put(serverId, ALBUM_TYPE, id.toString(), fetched, AlbumResource.serializer())
        ArrResult.ok(fetched)
    }

    /**
     * Selectable qualities of the artist's profile, flattened out of groups,
     * in profile order. Empty when the profile cannot be loaded; the file's
     * parsed quality then remains the only choice.
     */
    suspend fun qualityOptions(artist: ArtistResource): List<Quality> = withContext(io) {
        val profile = try {
            qualityProfileById(artist.qualityProfileId)
        } catch (_: Throwable) {
            null
        }
        profile?.items.orEmpty().flatMap(::flattenQualityItems)
    }

    /**
     * Imports the approved decisions, posting each file's chosen quality
     * (falling back to what Lidarr parsed) alongside the file's own matched
     * artist, album, release, and tracks. Offline the POST is enqueued.
     */
    suspend fun importSelections(
        decisions: List<ImportDecision>,
    ): ArrResult<WriteOutcome> = withContext(io) {
        if (decisions.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val body = decisions.map(::toUpdate)
        val failure = try {
            manualImportApi.updateManualImport(body)
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
                        appType = AppType.LIDARR,
                        method = "POST",
                        path = IMPORT_PATH,
                        bodyJson = json.encodeToString(
                            ListSerializer(ManualImportUpdateResource.serializer()),
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

    /** Wire shape of one confirmed selection: the file's match plus the quality decision. */
    private fun toUpdate(decision: ImportDecision): ManualImportUpdateResource {
        val resource = decision.resource
        val quality = if (decision.quality != null) {
            resource.quality?.copy(quality = decision.quality)
                ?: QualityModel(quality = decision.quality)
        } else {
            resource.quality
        }
        return ManualImportUpdateResource(
            id = resource.id,
            path = resource.path,
            name = resource.name,
            artistId = resource.artist?.id ?: 0,
            albumId = resource.album?.id ?: 0,
            albumReleaseId = resource.albumReleaseId,
            tracks = resource.tracks,
            trackIds = resource.tracks.orEmpty().map { it.id },
            quality = quality,
            releaseGroup = resource.releaseGroup,
            indexerFlags = resource.indexerFlags,
            downloadId = resource.downloadId,
            additionalFile = resource.additionalFile,
            replaceExistingFiles = resource.replaceExistingFiles,
            disableReleaseSwitching = resource.disableReleaseSwitching,
            rejections = resource.rejections,
        )
    }

    private fun flattenQualityItems(
        item: QualityProfileQualityItemResource,
    ): List<Quality> {
        // A concrete quality rides the item itself and honors `allowed`;
        // Lidarr's quality is nullable, so groups (quality null) must fall
        // through to their children instead of matching the null checks.
        val concrete = item.quality?.takeIf { it.id != 0 || it.name != null }
        return when {
            concrete != null -> if (item.allowed) listOf(concrete) else emptyList()

            // Groups carry their enabled state on the children; the
            // container's own `allowed` is not meaningful, so always descend.
            else -> item.items.orEmpty().flatMap(::flattenQualityItems)
        }
    }

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal const val IMPORT_PATH = "api/v1/manualimport"
        internal const val PARENT_LABEL = ".."

        /** The filesystem endpoint's directory discriminators. */
        private val FOLDER_TYPES = setOf("folder", "drive")

        /**
         * Same type values as the library and detail groups' caches so the
         * groups share rows; restated here because those constants live in
         * another package family.
         */
        internal val ARTIST_TYPE = EntityType("lidarr.artist")
        internal val ALBUM_TYPE = EntityType("lidarr.album")
    }
}

private fun JsonObject.stringOf(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
