package dev.appolon.homelab.arr.feature.sonarr.detail.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.sonarr.model.RenameEpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonStatisticsResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the detail repository's cache-first reads, write-through syncs, and
 * every offline queueing path: season and episode monitoring, the episode
 * file editor, series delete, season pass, release grabs, interactive
 * import, commands, and the queued badge mapping.
 */
class SonarrSeriesDetailRepositoryTest {

    private val library = FakeSonarrLibrary()
    private val queue = FakeSonarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = FakeActionQueue()
    private val serverId = ServerId("server-1")
    private var profileFetches = 0
    private var posterResult: ArrResult<Unit> = ArrResult.ok(Unit)

    private val repository = SonarrSeriesDetailRepository(
        serverId = serverId,
        store = store,
        seriesApi = library.series,
        episodeApi = library.episode,
        episodeFileApi = library.episodeFile,
        renameEpisodeApi = library.renameEpisode,
        seasonPassApi = library.seasonPass,
        historyApi = queue.history,
        releaseApi = queue.release,
        manualImportApi = queue.manualImport,
        commandPoster = { _ -> posterResult },
        qualityProfileApi = {
            profileFetches++
            listOf(QualityProfileResource(id = 1, name = "Any HD"))
        },
        qualityDefinitionApi = {
            listOf(
                QualityDefinitionResource(id = 1, quality = Quality(id = 1, name = "HDTV-720p")),
                QualityDefinitionResource(id = 2, quality = Quality(id = 2, name = "WEBDL-1080p")),
            )
        },
        languageApi = { listOf(LanguageResource(id = 1, name = "English")) },
        tagApi = { listOf(TagResource(id = 4, label = "anime")) },
        actionQueue = actionQueue,
    )

    @Before
    fun seed() {
        library.seriesStore[5] = series()
        library.episodeStore[11] = episode(11, 1, monitored = true)
        library.episodeStore[12] = episode(12, 2, monitored = false)
        library.episodeFileStore[30] = file()
        library.renames.add(
            RenameEpisodeResource(
                id = 1,
                seriesId = 5,
                seasonNumber = 1,
                episodeFileId = 30,
                existingPath = "/tv/old.mkv",
                newPath = "/tv/new.mkv",
            ),
        )
        library.error = { null }
    }

    @Test
    fun `refresh caches series and episodes with files`() = runTest {
        val series = repository.refreshSeries(5)
        assertTrue(series is ArrResult.Ok)
        val episodes = repository.refreshEpisodes(5)
        assertTrue(episodes is ArrResult.Ok)

        assertEquals("Show", repository.cachedSeries(5)?.title)
        val cached = repository.observeEpisodes(5).first()
        assertEquals(listOf(11, 12), cached.map { it.id })
        assertTrue(cached.first().hasFile)
        assertEquals("HDTV-720p", cached.first().episodeFile.quality.quality.name)
    }

    @Test
    fun `stale cache flags trigger refetch`() = runTest {
        assertTrue(repository.seriesStale())
        repository.refreshSeries(5)
        assertFalse(repository.seriesStale())
    }

    @Test
    fun `season toggle syncs and writes through`() = runTest {
        val outcome = repository.setSeasonMonitored(series(), seasonNumber = 1, monitored = false)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertFalse(library.seriesStore[5]?.seasons.orEmpty().first().monitored)
        assertFalse(repository.cachedSeries(5)?.seasons.orEmpty().first().monitored)
        assertTrue(actionQueue.enqueued.isEmpty())
    }

    @Test
    fun `season toggle offline queues with optimistic cache`() = runTest {
        library.error = { op -> if (op == "Series.update") IOException("down") else null }

        val outcome = repository.setSeasonMonitored(series(), seasonNumber = 1, monitored = true)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = actionQueue.enqueued.single()
        assertEquals("PUT", action.method)
        assertEquals("api/v3/series/5", action.path)
        assertTrue(repository.cachedSeries(5)?.seasons.orEmpty().first().monitored)
    }

    @Test
    fun `episode monitoring offline queues and flips rows`() = runTest {
        repository.refreshEpisodes(5)
        library.error = { op -> if (op == "Episode.updateMonitor") IOException("down") else null }

        val outcome = repository.setEpisodesMonitored(5, listOf(11, 12), monitored = true)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/episode/monitor", actionQueue.enqueued.single().path)
        assertTrue(repository.observeEpisodes(5).first().all { it.monitored })
    }

    @Test
    fun `episode file editor offline queues and updates cache`() = runTest {
        repository.refreshEpisodes(5)
        library.error = { op -> if (op == "EpisodeFile.update") IOException("down") else null }
        val edited = file().copy(
            quality = QualityModel(quality = Quality(id = 2, name = "WEBDL-1080p")),
            releaseGroup = "GRP",
        )

        val outcome = repository.updateEpisodeFile(edited)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/episodefile/30", actionQueue.enqueued.single().path)
        val cached = repository.observeEpisodes(5).first().first { it.id == 11 }
        assertEquals("WEBDL-1080p", cached.episodeFile.quality.quality.name)
        assertEquals("GRP", cached.episodeFile.releaseGroup)
    }

    @Test
    fun `episode file delete offline queues and detaches from episodes`() = runTest {
        repository.refreshEpisodes(5)
        library.error = { op -> if (op == "EpisodeFile.delete") IOException("down") else null }

        val outcome = repository.deleteEpisodeFile(30)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("DELETE", actionQueue.enqueued.single().method)
        val cached = repository.observeEpisodes(5).first().first { it.id == 11 }
        assertFalse(cached.hasFile)
        assertEquals(0, cached.episodeFileId)
    }

    @Test
    fun `delete series syncs and purges cache`() = runTest {
        repository.refreshSeries(5)
        repository.refreshEpisodes(5)

        val outcome = repository.deleteSeries(5, deleteFiles = true, addImportListExclusion = false)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertNull(repository.cachedSeries(5))
        assertTrue(repository.observeEpisodes(5).first().isEmpty())
        assertNull(library.seriesStore[5])
    }

    @Test
    fun `delete series offline queues with flags in path`() = runTest {
        library.error = { op -> if (op == "Series.delete") IOException("down") else null }

        val outcome = repository.deleteSeries(5, deleteFiles = true, addImportListExclusion = true)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = actionQueue.enqueued.single()
        assertEquals("DELETE", action.method)
        assertEquals("api/v3/series/5?deleteFiles=true&addImportListExclusion=true", action.path)
        assertNull(repository.cachedSeries(5))
    }

    @Test
    fun `history refresh caches and reconciles dropped rows`() = runTest {
        queue.historyStore[1] = history(1, episodeId = 11)
        queue.historyStore[2] = history(2, episodeId = 12)

        repository.refreshHistory(5)
        assertEquals(2, repository.observeHistory(5).first().size)

        queue.historyStore.remove(2)
        repository.refreshHistory(5)
        assertEquals(listOf(1), repository.observeHistory(5).first().map { it.id })
    }

    @Test
    fun `season pass offline queues and merges cache`() = runTest {
        repository.refreshSeries(5)
        library.error = { op -> if (op == "SeasonPass.create") IOException("down") else null }
        val pass = dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource(
            series = listOf(
                dev.appolon.homelab.arr.api.sonarr.model.SeasonPassSeriesResource(
                    id = 5,
                    monitored = false,
                    seasons = listOf(SeasonResource(seasonNumber = 1, monitored = false)),
                ),
            ),
        )

        val outcome = repository.applySeasonPass(pass)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertFalse(repository.cachedSeries(5)?.monitored == true)
    }

    @Test
    fun `rename preview returns rows`() = runTest {
        val rows = repository.renamePreview(5)
        assertEquals(listOf("/tv/new.mkv"), (rows as ArrResult.Ok).value.map { it.newPath })
    }

    @Test
    fun `command failure queues and auth failure errors`() = runTest {
        posterResult = ArrResult.err(ArrError.Network(null))
        val queued = repository.runCommandBody(
            SonarrCommandBodies.episodesSearch(listOf(11)),
            "Search episodes",
        )
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/command", actionQueue.enqueued.single().path)
        assertTrue(actionQueue.enqueued.single().bodyJson.orEmpty().contains("EpisodesSearch"))

        posterResult = ArrResult.err(ArrError.Auth(401))
        val rejected = repository.runCommandBody(
            SonarrCommandBodies.seriesSearch(5),
            "Search series",
        )
        assertTrue(rejected is ArrResult.Err)
    }

    @Test
    fun `grab release offline queues`() = runTest {
        queue.error = { op -> if (op == "Release.create") IOException("down") else null }

        val outcome = repository.grabRelease(
            ReleaseResource(guid = "g1", seriesId = 5, title = "Show S01E01"),
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/release", actionQueue.enqueued.single().path)
    }

    @Test
    fun `interactive import offline queues payload`() = runTest {
        queue.error = { op -> if (op == "ManualImport.create") IOException("down") else null }

        val outcome = repository.importItems(
            listOf(ManualImportReprocessResource(id = 1, path = "/tv/file.mkv", seriesId = 5)),
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/manualimport", actionQueue.enqueued.single().path)
        assertTrue(actionQueue.enqueued.single().bodyJson.orEmpty().contains("/tv/file.mkv"))
    }

    @Test
    fun `lookups cache within ttl`() = runTest {
        repository.qualityProfiles()
        repository.qualityProfiles()
        assertEquals(1, profileFetches)

        val qualities = repository.qualityDefinitions() as ArrResult.Ok
        assertEquals(2, qualities.value.size)
    }

    @Test
    fun `queued badge maps command and series actions to this series`() = runTest {
        library.error = { op -> if (op == "Series.update") IOException("down") else null }
        repository.setSeasonMonitored(series(), 1, true)
        posterResult = ArrResult.err(ArrError.Network(null))
        repository.runCommandBody(SonarrCommandBodies.seasonSearch(5, 1), "Search season")

        val ids = repository.queuedSeriesIds().first()

        assertEquals(setOf(5), ids)
        assertEquals(2, repository.queuedCount().first())
    }

    private fun series() = SeriesResource(
        id = 5,
        title = "Show",
        monitored = true,
        qualityProfileId = 1,
        path = "/tv/Show",
        seasons = listOf(
            SeasonResource(
                seasonNumber = 1,
                monitored = false,
                statistics = SeasonStatisticsResource(
                    episodeCount = 2,
                    episodeFileCount = 1,
                    sizeOnDisk = 2048,
                    percentOfEpisodes = 50.0,
                ),
            ),
        ),
        tags = setOf(4),
    )

    private fun episode(id: Int, number: Int, monitored: Boolean) = EpisodeResource(
        id = id,
        seriesId = 5,
        seasonNumber = 1,
        episodeNumber = number,
        title = "Episode $number",
        monitored = monitored,
        hasFile = id == 11,
        episodeFileId = if (id == 11) 30 else 0,
        episodeFile = if (id == 11) file() else EpisodeFileResource(),
    )

    private fun file() = EpisodeFileResource(
        id = 30,
        seriesId = 5,
        seasonNumber = 1,
        size = 4096,
        quality = QualityModel(quality = Quality(id = 1, name = "HDTV-720p")),
    )

    private fun history(id: Int, episodeId: Int) = HistoryResource(
        id = id,
        episodeId = episodeId,
        seriesId = 5,
        sourceTitle = "Show.S01E01.720p",
        eventType = EpisodeHistoryEventType.GRABBED,
        date = "2026-10-0${id}T00:00:00Z",
    )
}
