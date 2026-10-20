package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SonarrInteractiveImportRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var queue: FakeSonarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var seriesFetches = 0
    private val seriesById = mutableMapOf<Int, SeriesResource>()
    private var profile: QualityProfileResource? = null
    private val languages = mutableListOf<LanguageResource>()
    private val folders = mutableListOf<Pair<String?, String>>()
    private var fileSystemError: Throwable? = null

    private val repository: SonarrInteractiveImportRepository
        get() = SonarrInteractiveImportRepository(
            seasonNumber = 2,
            serverId = serverId,
            store = store,
            manualImportApi = queue.manualImport,
            fileSystemCall = { path, _ ->
                fileSystemError?.let { throw it }
                listing(path)
            },
            seriesById = { id ->
                seriesFetches += 1
                seriesById[id]
            },
            languageTable = { languages.toList() },
            qualityProfileById = { profile },
            actionQueue = actionQueue,
            io = Dispatchers.Unconfined,
        )

    /** Raw filesystem listing, as the schema-less endpoint returns it. */
    private fun listing(path: String?) = buildJsonObject {
        path?.let { put("parent", "/") }
        put(
            "directories",
            kotlinx.serialization.json.buildJsonArray {
                folders.forEach { (type, name) ->
                    add(
                        buildJsonObject {
                            put("type", type)
                            put("name", name)
                            put("path", (path ?: "") + "/" + name)
                        },
                    )
                }
            },
        )
    }

    private fun candidate(
        id: Int,
        path: String,
        seriesId: Int = 5,
        seasonNumber: Int = 2,
        episodeIds: List<Int> = listOf(42),
    ) = ManualImportResource(
        id = id,
        path = path,
        relativePath = path.substringAfterLast('/'),
        size = 1_400_000_000L,
        series = SeriesResource(),
        seasonNumber = seasonNumber,
        episodes = episodeIds.map { episodeId ->
            EpisodeResource(id = episodeId, episodeNumber = 5)
        },
        quality = QualityModel(quality = Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
    )

    @Before
    fun setUp() {
        queue = FakeSonarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
        seriesById[5] = SeriesResource(
            id = 5,
            title = "Target Series",
            path = "/media/tv/Target Series",
            qualityProfileId = 2,
        )
        profile = QualityProfileResource(
            id = 2,
            items = listOf(
                QualityProfileQualityItemResource(
                    quality = Quality(id = 7, name = "WEBDL-1080p"),
                    allowed = true,
                ),
                QualityProfileQualityItemResource(
                    quality = Quality(id = 8, name = "Bluray-1080p"),
                    allowed = false,
                ),
                QualityProfileQualityItemResource(
                    name = "Group 720p",
                    items = listOf(
                        QualityProfileQualityItemResource(
                            quality = Quality(id = 9, name = "HDTV-720p"),
                            allowed = true,
                        ),
                    ),
                ),
            ),
        )
        languages += listOf(
            LanguageResource(id = 0, name = "unknown"),
            LanguageResource(id = 1, name = "English"),
            LanguageResource(id = 3, name = "German"),
        )
        folders += listOf("folder" to "season-02", "file" to "notes.txt", "drive" to "media")
    }

    @Test
    fun `listFolders keeps folders and drives drops files and prepends the parent`() = runTest {
        val root = repository.listFolders(null)
        val nested = repository.listFolders("/media")

        assertTrue(root is ArrResult.Ok)
        assertEquals(
            listOf("season-02", "media"),
            (root as ArrResult.Ok).value.map { it.name },
        )
        val parented = (nested as ArrResult.Ok).value
        assertEquals("..", parented.first().name)
        assertTrue(parented.first().isParent)
        assertEquals("/", parented.first().path)
    }

    @Test
    fun `listFolders reports the failure without throwing`() = runTest {
        fileSystemError = IOException("offline")

        val result = repository.listFolders("/media")

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `listFiles reports the failure without throwing`() = runTest {
        queue.error = { operation ->
            if (operation == "ManualImport.list") IOException("offline") else null
        }

        val files = repository.listFiles("/media/tv/Target Series/season-02")

        assertTrue(files is ArrResult.Err)
    }

    @Test
    fun `listFiles narrows to the season but keeps cross-matched series`() = runTest {
        queue.manualImports += listOf(
            candidate(1, "/media/tv/Target Series/season-02/a.mkv"),
            candidate(2, "/media/tv/Target Series/season-02/b.mkv", seriesId = 9),
            candidate(3, "/media/tv/Target Series/season-02/c.mkv", seasonNumber = 3),
            candidate(4, "/media/tv/Target Series/season-02/d.mkv"),
        )
        queue.manualImports[3] = queue.manualImports[3].copy(episodeFileId = 77)

        val files = repository.listFiles("/media/tv/Target Series/season-02")

        assertTrue(files is ArrResult.Ok)
        // The season 3 file and the file that already exists drop out; the
        // series 9 mismatch stays so the flow can re-pin it on import.
        assertEquals(
            listOf(1, 2),
            (files as ArrResult.Ok).value.map { it.id },
        )
    }

    @Test
    fun `series reads the shared library cache before the network`() = runTest {
        val first = repository.series(5)
        val fetchesAfterFirst = seriesFetches
        val second = repository.series(5)

        assertTrue(first is ArrResult.Ok)
        assertEquals("Target Series", (first as ArrResult.Ok).value.title)
        assertEquals(fetchesAfterFirst, seriesFetches)
        assertTrue(second is ArrResult.Ok)
        // The fetched series wrote through to the shared sonarr.series rows.
        assertTrue(
            store.get(
                serverId,
                SonarrInteractiveImportRepository.SERIES_TYPE,
                "5",
                SeriesResource.serializer(),
            ) != null,
        )
    }

    @Test
    fun `series reports missing rows as errors`() = runTest {
        val result = repository.series(99)

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `qualityOptions flattens groups and keeps only allowed qualities`() = runTest {
        val options = repository.qualityOptions(seriesById.getValue(5))

        assertEquals(listOf(7, 9), options.map { it.id })
    }

    @Test
    fun `languageOptions drops unknown rows and sorts by name`() = runTest {
        val options = repository.languageOptions()

        assertEquals(listOf("English", "German"), options.map { it.name })
    }

    @Test
    fun `importSelections forces the flow series and applies the overrides`() = runTest {
        val resource = candidate(1, "/media/tv/Target Series/season-02/a.mkv")

        val outcome = repository.importSelections(
            series = seriesById.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = resource,
                    quality = Quality(id = 9, name = "HDTV-720p"),
                    language = Language(id = 3, name = "German"),
                    selected = true,
                ),
            ),
        )

        assertTrue(
            outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value == WriteOutcome.Synced,
        )
        val body = queue.reprocessedImports.single()
        assertEquals(5, body.seriesId)
        assertEquals(2, body.seasonNumber)
        assertEquals(listOf(42), body.episodeIds)
        assertEquals(9, body.quality.quality.id)
        assertEquals(listOf(3), body.languages?.map { it.id })
        assertEquals(resource.path, body.path)
    }

    @Test
    fun `importSelections keeps parsed quality and language without decisions`() = runTest {
        repository.importSelections(
            series = seriesById.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = candidate(1, "/media/tv/Target Series/season-02/a.mkv"),
                    quality = null,
                    language = null,
                    selected = true,
                ),
            ),
        )

        val body = queue.reprocessedImports.single()
        assertEquals(7, body.quality.quality.id)
        assertEquals(listOf(1), body.languages?.map { it.id })
    }

    @Test
    fun `importSelections offline enqueues the POST for replay`() = runTest {
        queue.error = { operation ->
            if (operation == "ManualImport.create") IOException("offline") else null
        }

        val outcome = repository.importSelections(
            series = seriesById.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = candidate(1, "/media/tv/Target Series/season-02/a.mkv"),
                    quality = null,
                    language = null,
                    selected = true,
                ),
            ),
        )

        assertTrue(
            outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value is WriteOutcome.Queued,
        )
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/manualimport", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"seriesId\":5"))
    }

    @Test
    fun `importSelections does not queue terminal failures`() = runTest {
        queue.error = { operation ->
            if (operation == "ManualImport.create") {
                HttpException(Response.error<Unit>(409, "{}".toResponseBody()))
            } else {
                null
            }
        }

        val outcome = repository.importSelections(
            series = seriesById.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = candidate(1, "/media/tv/Target Series/season-02/a.mkv"),
                    quality = null,
                    language = null,
                    selected = true,
                ),
            ),
        )

        assertTrue(outcome is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `importSelections with an empty selection is a no-op`() = runTest {
        val outcome = repository.importSelections(seriesById.getValue(5), emptyList())

        assertTrue(
            outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value == WriteOutcome.Synced,
        )
        assertTrue(queue.reprocessedImports.isEmpty())
    }
}
