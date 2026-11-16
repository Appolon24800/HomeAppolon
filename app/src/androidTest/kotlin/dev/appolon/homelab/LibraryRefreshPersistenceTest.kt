package dev.appolon.homelab

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.lidarr.library.data.LidarrArtistsRepository
import dev.appolon.homelab.arr.feature.radarr.library.data.RadarrMoviesRepository
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrCollectionsRepository
import dev.appolon.homelab.arr.feature.sonarr.addnew.data.SonarrAddRepository
import dev.appolon.homelab.arr.feature.sonarr.library.data.SonarrSeriesRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRefreshPersistenceTest {
    private data class Library(
        val server: ServerId,
        val type: EntityType,
        val refresh: suspend () -> ArrResult<Unit>,
        val cachedIds: suspend () -> List<Int>,
        val isStale: suspend () -> Boolean,
        val clearRemote: () -> Unit,
    )

    @Test
    fun libraryRefreshPreservesOldRowsOnFailureAndPersistsFetchTime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "library-refresh-${System.nanoTime()}")
        val graph = ArrDataGraph(context, directory)
        var clock = 1_000L
        val radarr = FakeRadarrLibrary().apply { movies += MovieResource(id = 1, title = "Movie") }
        val sonarr = FakeSonarrLibrary().apply { seriesStore[1] = SeriesResource(id = 1, title = "Series") }
        val lidarr = FakeLidarrLibrary().apply { artists[1] = ArtistResource(id = 1, artistName = "Artist") }
        val sonarrDiscovery = FakeSonarrDiscovery()
        val radarrDiscovery = FakeRadarrDiscovery().apply {
            collections += CollectionResource(id = 1, title = "Collection")
        }
        val movies = RadarrMoviesRepository(
            serverId = ServerId("radarr"), store = graph.entityStore,
            movieApi = radarr.movie, movieEditorApi = radarr.movieEditor,
            qualityProfileApi = { emptyList() }, commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = graph.actionQueue, now = { clock },
        )
        val series = SonarrSeriesRepository(
            serverId = ServerId("sonarr"), store = graph.entityStore,
            seriesApi = sonarr.series, seriesEditorApi = sonarr.seriesEditor,
            seasonPassApi = sonarr.seasonPass, qualityProfileApi = { emptyList() },
            tagApi = { emptyList() }, commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = graph.actionQueue, now = { clock },
        )
        val artists = LidarrArtistsRepository(
            serverId = ServerId("lidarr"), store = graph.entityStore,
            artistApi = lidarr, artistEditorApi = lidarr, albumApi = lidarr, albumStudioApi = lidarr,
            qualityProfileApi = { emptyList() }, metadataProfileApi = { emptyList() },
            commandRunner = { _, _ -> ArrResult.ok(Unit) }, actionQueue = graph.actionQueue, now = { clock },
        )
        val addSeries = SonarrAddRepository(
            serverId = ServerId("sonarr-add"), store = graph.entityStore,
            seriesApi = sonarr.series, lookupRunner = { ArrResult.ok(emptyList()) },
            rootFolderApi = { emptyList() }, qualityProfileApi = { emptyList() },
            languageProfileApi = { emptyList() }, tagApi = { emptyList() },
            importListApi = sonarrDiscovery.importList,
            importListExclusionApi = sonarrDiscovery.importListExclusion,
            actionQueue = graph.actionQueue, now = { clock },
        )
        val collections = RadarrCollectionsRepository(
            serverId = ServerId("radarr-collections"), store = graph.entityStore,
            collectionApi = radarrDiscovery, movieApi = radarr.movie,
            commandRunner = { _, _ -> ArrResult.ok(Unit) },
            actionQueue = graph.actionQueue, now = { clock },
        )
        val libraries = listOf(
            Library(ServerId("radarr"), EntityType("radarr.movie"), { movies.refresh() },
                { movies.cachedMovies().map { it.id } }, { movies.isStale(500) }, { radarr.movies.clear() }),
            Library(ServerId("sonarr"), EntityType("sonarr.series"), { series.refresh() },
                { series.cachedSeries().map { it.id } }, { series.isStale(500) }, { sonarr.seriesStore.clear() }),
            Library(ServerId("lidarr"), EntityType("lidarr.artist"), { artists.refresh() },
                { artists.cachedArtists().map { it.id } }, { artists.isStale(500) }, { lidarr.artists.clear() }),
            Library(ServerId("sonarr-add"), EntityType("sonarr.series"), { addSeries.refreshSeries() },
                { addSeries.cachedSeries().map { it.id } }, { addSeries.isStale(500) }, { sonarr.seriesStore.clear() }),
            Library(ServerId("radarr-collections"), EntityType("radarr.collection"), { collections.refresh() },
                { collections.cachedCollections().map { it.id } },
                { graph.entityStore.lastFetchedAt(ServerId("radarr-collections"), EntityType("radarr.collection"))?.let { clock - it > 500 } ?: true },
                { radarrDiscovery.collections.clear() }),
        )
        try {
            libraries.forEach {
                assertTrue(it.refresh() is ArrResult.Ok)
                assertEquals(listOf(1), it.cachedIds())
            }
            clock = 2_000L
            libraries.forEach {
                assertTrue(it.refresh() is ArrResult.Ok)
                assertEquals(2_000L, graph.entityStore.lastFetchedAt(it.server, it.type))
            }
            clock = 2_501L
            libraries.forEach { assertTrue(it.isStale()) }
            SQLiteDatabase.openDatabase(graph.paths.database.file().path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("CREATE TRIGGER fail_library_insert BEFORE INSERT ON entities WHEN NEW.id = '3' BEGIN SELECT RAISE(ABORT, 'test storage failure'); END")
            }
            radarr.movies.clear()
            radarr.movies += MovieResource(id = 3, title = "Replacement")
            sonarr.seriesStore.clear()
            sonarr.seriesStore[3] = SeriesResource(id = 3, title = "Replacement")
            lidarr.artists.clear()
            lidarr.artists[3] = ArtistResource(id = 3, artistName = "Replacement")
            radarrDiscovery.collections.clear()
            radarrDiscovery.collections += CollectionResource(id = 3, title = "Replacement")
            libraries.forEach {
                assertTrue(it.refresh() is ArrResult.Err)
                assertEquals(listOf(1), it.cachedIds())
                assertEquals(2_000L, graph.entityStore.lastFetchedAt(it.server, it.type))
            }
            SQLiteDatabase.openDatabase(graph.paths.database.file().path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TRIGGER fail_library_insert")
            }
            clock = 3_000L
            libraries.forEach {
                it.clearRemote()
                assertTrue(it.refresh() is ArrResult.Ok)
                assertTrue(it.cachedIds().isEmpty())
                assertFalse(it.isStale())
            }
        } finally {
            graph.close()
        }
        val reopened = ArrDataGraph(context, directory)
        try {
            libraries.forEach {
                assertEquals(3_000L, reopened.entityStore.lastFetchedAt(it.server, it.type))
            }
        } finally {
            reopened.close()
            directory.deleteRecursively()
        }
    }
}
