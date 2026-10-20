package dev.appolon.homelab.arr.api.sonarr

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileListResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodesMonitoredResource
import dev.appolon.homelab.arr.api.sonarr.model.RenameEpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource

/**
 * Mutable, single-test library state.
 * Supply [latency] to delay calls and [error] to inject failures.
 * The callback receives the OpenAPI tag and method name, such as `Series.list`.
 * File-system actions have no disk effects. Schema-less GET bodies are configurable JSON.
 */
class FakeSonarrLibrary(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    val seriesStore: MutableMap<Int, SeriesResource> = linkedMapOf()
    val lookupCatalog: MutableList<SeriesResource> = mutableListOf()
    val episodeStore: MutableMap<Int, EpisodeResource> = linkedMapOf()
    val episodeFileStore: MutableMap<Int, EpisodeFileResource> = linkedMapOf()
    val renames: MutableList<RenameEpisodeResource> = mutableListOf()
    val folders: MutableMap<Int, JsonObject> = linkedMapOf()
    val covers: MutableMap<Pair<Int, String>, JsonObject> = linkedMapOf()

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun nextSeriesId(): Int = (seriesStore.keys.maxOrNull() ?: 0) + 1

    private fun missing(id: Int): Nothing = throw HttpException(
        Response.error<Any>(404, "Missing resource $id".toResponseBody())
    )

    val series: SeriesApi = object : SeriesApi {
        override suspend fun lookup(term: String?, language: Int?): List<SeriesResource> {
            before("Series.lookup")
            val query = term?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
            return lookupCatalog.filter {
                it.title?.contains(query, ignoreCase = true) == true ||
                    query.equals("tvdb:${it.tvdbId}", ignoreCase = true)
            }
        }

        override suspend fun list(
            tvdbId: Int?,
            includeSeasonImages: Boolean?
        ): List<SeriesResource> {
            before("Series.list")
            return seriesStore.values.filter { tvdbId == null || it.tvdbId == tvdbId }
        }

        override suspend fun create(
            body: SeriesResource
        ): SeriesResource {
            before("Series.create")
            return body.copy(id = body.id.takeIf { it > 0 } ?: nextSeriesId()).also {
                seriesStore[it.id] = it
            }
        }

        override suspend fun get(
            id: Int,
            includeSeasonImages: Boolean?
        ): SeriesResource {
            before("Series.get")
            return seriesStore[id] ?: missing(id)
        }

        override suspend fun update(
            id: String,
            moveFiles: Boolean?,
            body: SeriesResource
        ): SeriesResource {
            before("Series.update")
            return body.copy(id = id.toInt()).also { seriesStore[it.id] = it }
        }

        override suspend fun delete(
            id: Int,
            deleteFiles: Boolean?,
            addImportListExclusion: Boolean?
        ): Unit {
            before("Series.delete")
            return run { seriesStore.remove(id); Unit }
        }

    }

    val seriesEditor: SeriesEditorApi = object : SeriesEditorApi {
        override suspend fun update(
            body: SeriesEditorResource
        ): Unit {
            before("SeriesEditor.update")
            return run {
                body.seriesIds.orEmpty().forEach { id ->
                    seriesStore[id]?.let { old ->
                        val tags = when (body.applyTags) {
                            ApplyTags.ADD -> old.tags.orEmpty() + body.tags.orEmpty()
                            ApplyTags.REMOVE -> old.tags.orEmpty() - body.tags.orEmpty().toSet()
                            ApplyTags.REPLACE -> body.tags.orEmpty().toSet()
                        }
                        seriesStore[id] = old.copy(
                            monitored = body.monitored ?: old.monitored,
                            monitorNewItems = body.monitorNewItems,
                            qualityProfileId = body.qualityProfileId ?: old.qualityProfileId,
                            seriesType = body.seriesType,
                            seasonFolder = body.seasonFolder ?: old.seasonFolder,
                            rootFolderPath = body.rootFolderPath ?: old.rootFolderPath,
                            tags = if (body.tags == null) old.tags else tags
                        )
                    }
                }
            }
        }

        override suspend fun delete(
            body: SeriesEditorResource
        ): Unit {
            before("SeriesEditor.delete")
            return run { body.seriesIds.orEmpty().forEach { seriesStore.remove(it) } }
        }

    }

    val seriesFolder: SeriesFolderApi = object : SeriesFolderApi {
        override suspend fun get(
            id: Int
        ): JsonObject {
            before("SeriesFolder.get")
            return folders[id] ?: JsonObject(emptyMap())
        }

    }

    val seriesImport: SeriesImportApi = object : SeriesImportApi {
        override suspend fun create(
            body: List<SeriesResource>
        ): Unit {
            before("SeriesImport.create")
            return run { body.forEach { item -> seriesStore[item.id] = item } }
        }

    }

    val seasonPass: SeasonPassApi = object : SeasonPassApi {
        override suspend fun create(
            body: SeasonPassResource
        ): Unit {
            before("SeasonPass.create")
            return run {
                body.series.orEmpty().forEach { item ->
                    seriesStore[item.id]?.let { old ->
                        seriesStore[item.id] = old.copy(
                            monitored = item.monitored ?: old.monitored,
                            seasons = item.seasons ?: old.seasons
                        )
                    }
                }
            }
        }

    }

    val episode: EpisodeApi = object : EpisodeApi {
        override suspend fun list(
            seriesId: Int?,
            seasonNumber: Int?,
            episodeIds: List<Int>?,
            episodeFileId: Int?,
            includeSeries: Boolean?,
            includeEpisodeFile: Boolean?,
            includeImages: Boolean?
        ): List<EpisodeResource> {
            before("Episode.list")
            return episodeStore.values.filter {
                (seriesId == null || it.seriesId == seriesId) &&
                    (seasonNumber == null || it.seasonNumber == seasonNumber) &&
                    (episodeIds == null || it.id in episodeIds) &&
                    (episodeFileId == null || it.episodeFileId == episodeFileId)
            }.map {
                it.copy(
                    series = if (includeSeries == true) seriesStore[it.seriesId] ?: it.series
                        else SeriesResource(),
                    episodeFile = if (includeEpisodeFile == true) {
                        episodeFileStore[it.episodeFileId] ?: it.episodeFile
                    } else EpisodeFileResource(),
                    images = if (includeImages == true) it.images else null
                )
            }
        }

        override suspend fun update(
            id: Int,
            body: EpisodeResource
        ): EpisodeResource {
            before("Episode.update")
            return body.copy(id = id).also { episodeStore[id] = it }
        }

        override suspend fun get(
            id: Int
        ): Response<EpisodeResource> {
            before("Episode.get")
            return episodeStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "".toResponseBody())
        }

        override suspend fun updateMonitor(
            includeImages: Boolean?,
            body: EpisodesMonitoredResource
        ): Unit {
            before("Episode.updateMonitor")
            return run {
                body.episodeIds.orEmpty().forEach { id ->
                    episodeStore[id]?.let { episodeStore[id] = it.copy(monitored = body.monitored) }
                }
            }
        }

    }

    val episodeFile: EpisodeFileApi = object : EpisodeFileApi {
        override suspend fun list(
            seriesId: Int?,
            episodeFileIds: List<Int>?
        ): List<EpisodeFileResource> {
            before("EpisodeFile.list")
            return episodeFileStore.values.filter {
                (seriesId == null || it.seriesId == seriesId) &&
                    (episodeFileIds == null || it.id in episodeFileIds)
            }
        }

        override suspend fun update(
            id: String,
            body: EpisodeFileResource
        ): EpisodeFileResource {
            before("EpisodeFile.update")
            return body.copy(id = id.toInt()).also { episodeFileStore[it.id] = it }
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("EpisodeFile.delete")
            return run { episodeFileStore.remove(id); Unit }
        }

        override suspend fun get(
            id: Int
        ): Response<EpisodeFileResource> {
            before("EpisodeFile.get")
            return episodeFileStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "".toResponseBody())
        }

        @Deprecated("Deprecated by Sonarr; use updateBulk instead")
        override suspend fun updateEditor(
            body: EpisodeFileListResource
        ): Unit {
            before("EpisodeFile.updateEditor")
            return run {
                body.episodeFileIds.orEmpty().forEach { id ->
                    episodeFileStore[id]?.let {
                        episodeFileStore[id] = it.copy(
                            languages = body.languages ?: it.languages,
                            quality = body.quality,
                            sceneName = body.sceneName ?: it.sceneName,
                            releaseGroup = body.releaseGroup ?: it.releaseGroup
                        )
                    }
                }
            }
        }

        override suspend fun deleteBulk(
            body: EpisodeFileListResource
        ): Unit {
            before("EpisodeFile.deleteBulk")
            return run { body.episodeFileIds.orEmpty().forEach { episodeFileStore.remove(it) } }
        }

        override suspend fun updateBulk(
            body: List<EpisodeFileResource>
        ): Unit {
            before("EpisodeFile.updateBulk")
            return run { body.forEach { episodeFileStore[it.id] = it } }
        }

    }

    val renameEpisode: RenameEpisodeApi = object : RenameEpisodeApi {
        override suspend fun list(
            seriesId: Int?,
            seasonNumber: Int?
        ): List<RenameEpisodeResource> {
            before("RenameEpisode.list")
            return renames.filter {
                (seriesId == null || it.seriesId == seriesId) &&
                    (seasonNumber == null || it.seasonNumber == seasonNumber)
            }
        }

        override suspend fun listBulk(
            seriesIds: List<Int>?
        ): List<RenameEpisodeResource> {
            before("RenameEpisode.listBulk")
            return renames.filter { seriesIds == null || it.seriesId in seriesIds }
        }

    }

    val mediaCover: MediaCoverApi = object : MediaCoverApi {
        override suspend fun get(
            seriesId: Int,
            filename: String
        ): JsonObject {
            before("MediaCover.get")
            return covers[seriesId to filename] ?: JsonObject(emptyMap())
        }

    }
}
