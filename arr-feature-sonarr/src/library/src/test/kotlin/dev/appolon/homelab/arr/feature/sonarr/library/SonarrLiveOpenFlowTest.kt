package dev.appolon.homelab.arr.feature.sonarr.library

import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.SonarrSeriesRepository
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.Properties

/**
 * Replays the exact open-Sonarr flow against the live server: build the
 * production API bundle, refresh the repository, and drive the presentation
 * mapping. Reproduces field crashes with real stack traces (no R8).
 */
class SonarrLiveOpenFlowTest {

    private fun liveServer(): Triple<String, String, Unit>? = runCatching {
        val props = Properties().apply {
            File("../local.arr-test.properties").inputStream().use { load(it) }
        }
        val url = props.getProperty("sonarr.url") ?: return null
        val key = props.getProperty("sonarr.apikey") ?: return null
        Triple(url, key, Unit)
    }.getOrNull()

    @Test
    fun openSonarrFlowAgainstLiveServer() {
        val live = liveServer()
        assumeTrue("local.arr-test.properties missing; self-skip", live != null)
        val (url, key) = live!!

        runBlocking {
            val endpoint = ServerEndpoint(
                serverId = dev.appolon.homelab.arr.core.ServerId("live-sonarr"),
                baseUrl = url,
                apiKey = key,
            )
            val client = DefaultArrClientFactory().clientFor(endpoint)
            val baseUrl = ServerEndpoint.normalizeBaseUrl(url)
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
            val seriesApi = dev.appolon.homelab.arr.api.sonarr.SonarrLibraryApis.series(retrofit)
            val qualityProfileApi = retrofit.create(
                dev.appolon.homelab.arr.api.sonarr.QualityProfileApi::class.java,
            )
            val tagApi = dev.appolon.homelab.arr.api.sonarr.SonarrSystemApis.tag(retrofit)
            val commandRunner =
                dev.appolon.homelab.arr.feature.sonarr.library.OkHttpSonarrCommandRunner(client, baseUrl)

            val store = FakeEntityStore()
            val queue = object : dev.appolon.homelab.arr.data.ActionQueue {
                override suspend fun enqueue(action: dev.appolon.homelab.arr.data.QueuedAction) {}
                override suspend fun pending(): List<dev.appolon.homelab.arr.data.QueuedAction> = emptyList()
                override fun observe() = kotlinx.coroutines.flow.MutableStateFlow<List<dev.appolon.homelab.arr.data.QueuedAction>>(emptyList())
                override suspend fun replay(executor: suspend (dev.appolon.homelab.arr.data.QueuedAction) -> Boolean) = 0 to 0
                override suspend fun remove(actionId: dev.appolon.homelab.arr.core.ActionId) {}
            }

            val repo = SonarrSeriesRepository(
                serverId = dev.appolon.homelab.arr.core.ServerId("live-sonarr"),
                store = store,
                seriesApi = seriesApi,
                seriesEditorApi = dev.appolon.homelab.arr.api.sonarr.SonarrLibraryApis.seriesEditor(retrofit),
                seasonPassApi = dev.appolon.homelab.arr.api.sonarr.SonarrLibraryApis.seasonPass(retrofit),
                qualityProfileApi = { qualityProfileApi.list() },
                tagApi = { tagApi.list() },
                commandRunner = commandRunner,
                actionQueue = queue,
            )

            // The exact first-screen refresh the app performs on open.
            when (val result = repo.refresh()) {
                is ArrResult.Err -> error("refresh failed: ${result.error}")
                is ArrResult.Ok -> Unit
            }

            // Drive the presentation layer exactly as the list screen does:
            // read cached series, apply default filters, build poster URLs.
            val cached = repo.cachedSeries()
            assumeTrue("no series on live server", cached.isNotEmpty())
            val filtered = dev.appolon.homelab.arr.feature.sonarr.library.data.presentSeries(
                cached,
                dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesLibraryFilters.Options(),
                dev.appolon.homelab.arr.core.FilterState(),
                sortId = "title",
                descending = false,
                visibleCount = cached.size,
            )
            val posters = cached.map { series ->
                dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesPosterUrls
                    .posterUrlOf(series, baseUrl)
            }
            check(posters.size == cached.size)
            println("live sonarr open flow OK: ${cached.size} series, ${filtered.all.size} filtered")
        }
    }
}
