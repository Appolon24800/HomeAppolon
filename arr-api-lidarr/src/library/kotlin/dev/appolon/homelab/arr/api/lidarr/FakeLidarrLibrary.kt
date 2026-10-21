package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumsMonitoredResource
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileListResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * In-memory library clients for UI tests. Seed the public collections before use.
 * [latency] can call a test scheduler's delay without adding a coroutine runtime dependency.
 * [error] runs after latency and before any mutation, keyed by the API method name.
 * Cover responses are JSON stand-ins because the specification provides no response schema.
 */
class FakeLidarrLibrary(
    var latency: suspend (operation: String) -> Unit = {},
    var error: (operation: String) -> Throwable? = { null },
) : ArtistApi, ArtistEditorApi, ArtistLookupApi, AlbumApi, AlbumLookupApi, AlbumStudioApi,
    TrackApi, TrackFileApi, RenameTrackApi, RetagTrackApi, MediaCoverApi {
    val artists: MutableMap<Int, ArtistResource> = linkedMapOf()
    val albums: MutableMap<Int, AlbumResource> = linkedMapOf()
    val tracks: MutableMap<Int, TrackResource> = linkedMapOf()
    val trackFiles: MutableMap<Int, TrackFileResource> = linkedMapOf()
    val trackReleaseIds: MutableMap<Int, Int> = linkedMapOf()
    val renames: MutableList<RenameTrackResource> = mutableListOf()
    val retags: MutableList<RetagTrackResource> = mutableListOf()
    val artistCovers: MutableMap<Pair<Int, String>, JsonObject> = linkedMapOf()
    val albumCovers: MutableMap<Pair<Int, String>, JsonObject> = linkedMapOf()
    val excludedArtistIds: MutableSet<Int> = linkedSetOf()
    val excludedAlbumIds: MutableSet<Int> = linkedSetOf()
    val deletedTrackFileIds: MutableSet<Int> = linkedSetOf()

    /** Every API method invoked, in order; asserts which calls a flow makes. */
    val requested: MutableList<String> = mutableListOf()

    private val empty = JsonObject(emptyMap())

    private suspend fun before(operation: String) {
        requested += operation
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missing(): Nothing = throw HttpException(
        Response.error<JsonObject>(404, "{}".toResponseBody("application/json".toMediaType()))
    )

    private fun nextId(ids: Set<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    override suspend fun getArtist(id: Int): ArtistResource {
        before("getArtist")
        return artists[id] ?: missing()
    }

    override suspend fun getArtists(mbId: String?): List<ArtistResource> {
        before("getArtists")
        return artists.values.filter { mbId == null || it.mbId == mbId }
    }

    override suspend fun addArtist(body: ArtistResource): ArtistResource {
        before("addArtist")
        val value = body.copy(id = body.id.takeIf { it != 0 } ?: nextId(artists.keys))
        artists[value.id] = value
        return value
    }

    override suspend fun updateArtist(
        id: String,
        moveFiles: Boolean?,
        body: ArtistResource,
    ): ArtistResource {
        before("updateArtist")
        val key = id.toIntOrNull() ?: missing()
        if (key !in artists) missing()
        return body.copy(id = key).also { artists[key] = it }
    }

    private fun removeArtist(id: Int, deleteFiles: Boolean?, exclude: Boolean?) {
        if (artists.remove(id) == null) missing()
        if (exclude == true) excludedArtistIds.add(id)
        if (deleteFiles == true) {
            val ids = trackFiles.values.filter { it.artistId == id }.map { it.id }
            ids.forEach(::removeTrackFile)
        }
        albums.values.filter { it.artistId == id }.map { it.id }.forEach { albums.remove(it) }
        tracks.values.filter { it.artistId == id }.map { it.id }.forEach { tracks.remove(it) }
    }

    override suspend fun deleteArtist(
        id: Int,
        deleteFiles: Boolean?,
        addImportListExclusion: Boolean?,
    ): JsonObject {
        before("deleteArtist")
        removeArtist(id, deleteFiles, addImportListExclusion)
        return empty
    }

    override suspend fun updateArtists(body: ArtistEditorResource): JsonObject {
        before("updateArtists")
        val ids = body.artistIds.orEmpty()
        if (ids.any { it !in artists }) missing()
        ids.forEach { id ->
            val old = artists.getValue(id)
            val tags = when (body.applyTags) {
                ApplyTags.ADD -> (old.tags.orEmpty() + body.tags.orEmpty()).distinct()
                ApplyTags.REMOVE -> old.tags.orEmpty() - body.tags.orEmpty().toSet()
                ApplyTags.REPLACE -> body.tags.orEmpty()
            }
            artists[id] = old.copy(
                monitored = body.monitored ?: old.monitored,
                monitorNewItems = body.monitorNewItems,
                qualityProfileId = body.qualityProfileId ?: old.qualityProfileId,
                metadataProfileId = body.metadataProfileId ?: old.metadataProfileId,
                rootFolderPath = body.rootFolderPath ?: old.rootFolderPath,
                tags = if (body.tags == null) old.tags else tags,
            )
        }
        return empty
    }

    override suspend fun deleteArtists(body: ArtistEditorResource): JsonObject {
        before("deleteArtists")
        val ids = body.artistIds.orEmpty()
        if (ids.any { it !in artists }) missing()
        ids.forEach { removeArtist(it, body.deleteFiles, body.addImportListExclusion) }
        return empty
    }

    override suspend fun lookupArtists(term: String?): List<ArtistResource> {
        before("lookupArtists")
        return artists.values.filter {
            term.isNullOrEmpty() || it.artistName.orEmpty().contains(term, ignoreCase = true) ||
                it.foreignArtistId == term.removePrefix("lidarr:")
        }
    }

    override suspend fun getAlbums(
        artistId: Int?,
        albumIds: List<Int>?,
        foreignAlbumId: String?,
        includeAllArtistAlbums: Boolean?,
    ): List<AlbumResource> {
        before("getAlbums")
        return albums.values.filter {
            (artistId == null || it.artistId == artistId) &&
                (albumIds == null || it.id in albumIds) &&
                (foreignAlbumId == null || it.foreignAlbumId == foreignAlbumId)
        }
    }

    override suspend fun getAlbum(id: Int): AlbumResource {
        before("getAlbum")
        return albums[id] ?: missing()
    }

    override suspend fun addAlbum(body: AlbumResource): AlbumResource {
        before("addAlbum")
        val value = body.copy(id = body.id.takeIf { it != 0 } ?: nextId(albums.keys))
        albums[value.id] = value
        return value
    }

    override suspend fun updateAlbum(id: String, body: AlbumResource): AlbumResource {
        before("updateAlbum")
        val key = id.toIntOrNull() ?: missing()
        if (key !in albums) missing()
        return body.copy(id = key).also { albums[key] = it }
    }

    override suspend fun deleteAlbum(
        id: Int,
        deleteFiles: Boolean?,
        addImportListExclusion: Boolean?,
    ): JsonObject {
        before("deleteAlbum")
        if (albums.remove(id) == null) missing()
        if (addImportListExclusion == true) excludedAlbumIds.add(id)
        if (deleteFiles == true) {
            trackFiles.values.filter { it.albumId == id }.map { it.id }.forEach(::removeTrackFile)
        }
        tracks.values.filter { it.albumId == id }.map { it.id }.forEach { tracks.remove(it) }
        return empty
    }

    override suspend fun monitorAlbums(body: AlbumsMonitoredResource): JsonObject {
        before("monitorAlbums")
        val ids = body.albumIds.orEmpty()
        if (ids.any { it !in albums }) missing()
        ids.forEach { albums[it] = albums.getValue(it).copy(monitored = body.monitored) }
        return empty
    }

    override suspend fun lookupAlbums(term: String?): List<AlbumResource> {
        before("lookupAlbums")
        return albums.values.filter {
            term.isNullOrEmpty() || it.title.orEmpty().contains(term, ignoreCase = true) ||
                it.foreignAlbumId == term.removePrefix("lidarr:")
        }
    }

    override suspend fun updateAlbumStudio(body: AlbumStudioResource): JsonObject {
        before("updateAlbumStudio")
        val entries = body.artist.orEmpty()
        if (entries.any { it.id !in artists }) missing()
        entries.forEach { entry ->
            val old = artists.getValue(entry.id)
            artists[entry.id] = old.copy(
                monitored = entry.monitored ?: old.monitored,
                monitorNewItems = body.monitorNewItems,
            )
            val options = body.monitoringOptions
            albums.values.filter { it.artistId == entry.id }.forEach { album ->
                val explicit = entry.albums?.firstOrNull { it.id == album.id }?.monitored
                val monitored = explicit ?: when (options?.monitor) {
                    MonitorTypes.ALL -> true
                    MonitorTypes.NONE -> false
                    else -> options?.monitored ?: album.monitored
                }
                albums[album.id] = album.copy(monitored = monitored)
            }
        }
        return empty
    }

    override suspend fun getTracks(
        artistId: Int?,
        albumId: Int?,
        albumReleaseId: Int?,
        trackIds: List<Int>?,
    ): List<TrackResource> {
        before("getTracks")
        return tracks.values.filter {
            (artistId == null || it.artistId == artistId) &&
                (albumId == null || it.albumId == albumId) &&
                (albumReleaseId == null || trackReleaseIds[it.id] == albumReleaseId) &&
                (trackIds == null || it.id in trackIds)
        }
    }

    override suspend fun getTrack(id: Int): TrackResource {
        before("getTrack")
        return tracks[id] ?: missing()
    }

    override suspend fun getTrackFiles(
        artistId: Int?,
        trackFileIds: List<Int>?,
        albumId: List<Int>?,
        unmapped: Boolean?,
    ): List<TrackFileResource> {
        before("getTrackFiles")
        return trackFiles.values.filter {
            (artistId == null || it.artistId == artistId) &&
                (trackFileIds == null || it.id in trackFileIds) &&
                (albumId == null || it.albumId in albumId) &&
                (unmapped == null || (it.albumId == 0) == unmapped)
        }
    }

    override suspend fun getTrackFile(id: Int): TrackFileResource {
        before("getTrackFile")
        return trackFiles[id] ?: missing()
    }

    override suspend fun updateTrackFile(id: String, body: TrackFileResource): TrackFileResource {
        before("updateTrackFile")
        val key = id.toIntOrNull() ?: missing()
        if (key !in trackFiles) missing()
        return body.copy(id = key).also { trackFiles[key] = it }
    }

    private fun removeTrackFile(id: Int) {
        if (trackFiles.remove(id) == null) missing()
        deletedTrackFileIds.add(id)
        tracks.values.filter { it.trackFileId == id }.forEach {
            tracks[it.id] = it.copy(trackFileId = 0, hasFile = false, trackFile = null)
        }
    }

    override suspend fun deleteTrackFile(id: Int): JsonObject {
        before("deleteTrackFile")
        removeTrackFile(id)
        return empty
    }

    override suspend fun updateTrackFiles(body: TrackFileListResource): JsonObject {
        before("updateTrackFiles")
        val ids = body.trackFileIds.orEmpty()
        if (ids.any { it !in trackFiles }) missing()
        ids.forEach { id ->
            val old = trackFiles.getValue(id)
            trackFiles[id] = old.copy(
                quality = body.quality ?: old.quality,
                sceneName = body.sceneName ?: old.sceneName,
                releaseGroup = body.releaseGroup ?: old.releaseGroup,
            )
        }
        return empty
    }

    override suspend fun deleteTrackFiles(body: TrackFileListResource): JsonObject {
        before("deleteTrackFiles")
        val ids = body.trackFileIds.orEmpty().distinct()
        if (ids.any { it !in trackFiles }) missing()
        ids.forEach(::removeTrackFile)
        return empty
    }

    override suspend fun getTrackRenames(artistId: Int?, albumId: Int?): List<RenameTrackResource> {
        before("getTrackRenames")
        return renames.filter {
            (artistId == null || it.artistId == artistId) &&
                (albumId == null || it.albumId == albumId)
        }
    }

    override suspend fun getTrackRetags(artistId: Int?, albumId: Int?): List<RetagTrackResource> {
        before("getTrackRetags")
        return retags.filter {
            (artistId == null || it.artistId == artistId) &&
                (albumId == null || it.albumId == albumId)
        }
    }

    override suspend fun getArtistCover(artistId: Int, filename: String): JsonObject {
        before("getArtistCover")
        return artistCovers[artistId to filename] ?: missing()
    }

    override suspend fun getAlbumCover(albumId: Int, filename: String): JsonObject {
        before("getAlbumCover")
        return albumCovers[albumId to filename] ?: missing()
    }
}
