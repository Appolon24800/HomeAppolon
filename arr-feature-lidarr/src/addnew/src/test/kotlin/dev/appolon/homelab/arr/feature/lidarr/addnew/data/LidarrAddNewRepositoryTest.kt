package dev.appolon.homelab.arr.feature.lidarr.addnew.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrDiscovery
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.MonitoringOptions
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.addnew.FakeActionQueue
import dev.appolon.homelab.arr.feature.lidarr.addnew.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrAddNewRepositoryTest {

    private val serverId = ServerId("s1")

    private lateinit var library: FakeLidarrLibrary
    private lateinit var system: FakeLidarrSystem
    private lateinit var calendar: FakeLidarrCalendar
    private lateinit var discovery: FakeLidarrDiscovery
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: LidarrAddNewRepository

    @Before
    fun setUp() {
        library = FakeLidarrLibrary()
        system = FakeLidarrSystem()
        calendar = FakeLidarrCalendar()
        discovery = FakeLidarrDiscovery()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        repository = LidarrAddNewRepository(
            serverId = serverId,
            store = store,
            artistLookupApi = library,
            artistApi = library,
            albumLookupApi = library,
            albumApi = library,
            albumStudioApi = library,
            importListApi = discovery,
            rootFolderApi = system,
            tagApi = system,
            qualityProfileApi = calendar,
            metadataProfileApi = calendar,
            actionQueue = queue,
            io = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    }

    private fun libraryArtist(
        id: Int,
        name: String = "Aurora",
        foreignId: String = "mb-artist-$id",
    ) = ArtistResource(id = id, artistName = name, foreignArtistId = foreignId)

    @Test
    fun `searchArtists hits the lookup endpoint and passes the term through`() = runTest {
        library.artists[1] = libraryArtist(1, name = "Aurora")
        library.artists[2] = libraryArtist(2, name = "Autechre")
        val result = repository.searchArtists("aur")
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Aurora"), (result as ArrResult.Ok).value.map { it.artistName })
    }

    @Test
    fun `syncLibraryIfStale caches artists and never requests the album list`() = runTest {
        val artist = libraryArtist(1)
        library.artists[1] = artist
        library.albums[10] = AlbumResource(id = 10, title = "Album", foreignAlbumId = "mb-al-10", artistId = 1)
        val result = repository.syncLibraryIfStale()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf(artist), store.getAll(serverId, LidarrAddNewRepository.ARTIST_TYPE, ArtistResource.serializer()))
        // The unfiltered album list OOM-killed the process on large
        // libraries; albums only arrive through scoped fetches.
        assertTrue(library.requested.none { it.startsWith("album") })
        assertTrue(store.getAll(serverId, LidarrAddNewRepository.ALBUM_TYPE, AlbumResource.serializer()).isEmpty())
    }

    @Test
    fun `syncLibraryIfStale honors the TTL and keeps optimistic rows`() = runTest {
        library.artists[1] = libraryArtist(1)
        repository.syncLibraryIfStale()
        // An offline add lands an optimistic row after the sync.
        store.put(
            serverId,
            LidarrAddNewRepository.ARTIST_TYPE,
            LidarrAddNewRepository.optimisticKey("mb-offline"),
            ArtistResource(artistName = "Offline", foreignArtistId = "mb-offline"),
            ArtistResource.serializer(),
        )
        // Server forgets artist 1 and the network dies: a stale sync fails,
        // a fresh TTL window skips the network entirely.
        library.artists.clear()
        library.error = { IOException("plane mode") }
        val fresh = repository.syncLibraryIfStale()
        assertTrue(fresh is ArrResult.Ok)
        val cached = store.getAll(serverId, LidarrAddNewRepository.ARTIST_TYPE, ArtistResource.serializer())
        assertEquals(setOf("Aurora", "Offline"), cached.map { it.artistName }.toSet())
    }

    @Test
    fun `catalogs are cache-first and survive a dead network within the TTL`() = runTest {
        system.rootFolders[1] = dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource(
            id = 1,
            path = "/media/music",
            freeSpace = 5_000L,
        )
        val first = repository.rootFolders()
        assertTrue(first is ArrResult.Ok)
        system.error = { IOException("plane mode") }
        val second = repository.rootFolders()
        assertTrue(second is ArrResult.Ok)
        assertEquals(listOf("/media/music"), (second as ArrResult.Ok).value.map { it.path })
    }

    @Test
    fun `importLists caches the browse feed`() = runTest {
        discovery.importLists[1] = ImportListResource(id = 1, name = "Spotify saved", enableAutomaticAdd = true)
        val result = repository.importLists()
        assertTrue(result is ArrResult.Ok)
        assertEquals(1, (result as ArrResult.Ok).value.size)
        assertEquals(
            1,
            store.getAll(serverId, LidarrAddNewRepository.IMPORT_LIST_TYPE, ImportListResource.serializer()).size,
        )
    }

    @Test
    fun `addArtist writes the created row through to the shared artist type`() = runTest {
        val outcome = repository.addArtist(
            ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"),
        )
        assertTrue(outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value is AddOutcome.Synced)
        val created = library.artists.values.single()
        val cached = store.getAll(serverId, LidarrAddNewRepository.ARTIST_TYPE, ArtistResource.serializer())
        assertEquals(listOf(created.id.toString()), cached.map { it.id.toString() })
    }

    @Test
    fun `addArtist offline queues and applies the row optimistically`() = runTest {
        library.error = { if (it == "addArtist") IOException("plane mode") else null }
        val outcome = repository.addArtist(
            ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"),
        )
        assertTrue(outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value is AddOutcome.Queued)
        assertEquals(1, queue.pending().size)
        assertEquals("api/v1/artist", queue.pending().single().path)
        val optimisticKey = LidarrAddNewRepository.optimisticKey("mb-new")
        assertTrue(optimisticKey in store.rowKeys().map { it.third })
        assertEquals(setOf("mb-new"), repository.queuedForeignArtistIds().first())
    }

    @Test
    fun `addAlbum writes through and queues offline like artists`() = runTest {
        val synced = repository.addAlbum(
            AlbumResource(title = "New Album", foreignAlbumId = "mb-al-new", artistId = 1),
        )
        assertTrue(synced is ArrResult.Ok && (synced as ArrResult.Ok).value is AddOutcome.Synced)
        library.error = { if (it == "addAlbum") IOException("plane mode") else null }
        val queued = repository.addAlbum(
            AlbumResource(title = "Offline Album", foreignAlbumId = "mb-al-off", artistId = 1),
        )
        assertTrue(queued is ArrResult.Ok && (queued as ArrResult.Ok).value is AddOutcome.Queued)
        assertEquals("api/v1/album", queue.pending().single().path)
        assertTrue(
            LidarrAddNewRepository.optimisticKey("mb-al-off") in
                store.rowKeys().map { it.third },
        )
    }


    /** Seeds a cached album row the way scoped fetches do. */
    private suspend fun seedCachedAlbum(album: AlbumResource) {
        store.put(
            serverId,
            LidarrAddNewRepository.ALBUM_TYPE,
            album.id.toString(),
            album,
            AlbumResource.serializer(),
        )
    }

    @Test
    fun `discography refreshes the artist albums and keeps other artists rows`() = runTest {
        library.artists[1] = libraryArtist(1)
        library.artists[2] = libraryArtist(2)
        library.albums[10] = AlbumResource(id = 10, title = "Mine", foreignAlbumId = "mb-10", artistId = 1)
        library.albums[20] = AlbumResource(id = 20, title = "Other", foreignAlbumId = "mb-20", artistId = 2)
        seedCachedAlbum(library.albums.getValue(20))
        val result = repository.discography(1)
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Mine"), (result as ArrResult.Ok).value.map { it.title })
        // The artist-scoped refresh never deletes other artists' cached rows.
        assertEquals(
            listOf("Mine", "Other"),
            store.getAll(serverId, LidarrAddNewRepository.ALBUM_TYPE, AlbumResource.serializer())
                .sortedBy { it.id }
                .map { it.title },
        )
    }

    @Test
    fun `applyAlbumStudio syncs and rewrites the cached monitored state`() = runTest {
        library.artists[1] = libraryArtist(1)
        library.albums[10] = AlbumResource(id = 10, title = "First", foreignAlbumId = "mb-10", artistId = 1)
        library.albums[11] = AlbumResource(id = 11, title = "Second", foreignAlbumId = "mb-11", artistId = 1)
        seedCachedAlbum(library.albums.getValue(10))
        seedCachedAlbum(library.albums.getValue(11))
        val payload = AlbumStudioResource(
            artist = listOf(
                dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioArtistResource(
                    id = 1,
                    albums = listOf(
                        AlbumResource(id = 10, monitored = true),
                        AlbumResource(id = 11, monitored = false),
                    ),
                ),
            ),
            monitoringOptions = MonitoringOptions(monitor = MonitorTypes.NONE),
        )
        val outcome = repository.applyAlbumStudio(payload)
        assertTrue(outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value is AddOutcome.Synced)
        val cached = store.getAll(serverId, LidarrAddNewRepository.ALBUM_TYPE, AlbumResource.serializer())
            .sortedBy { it.id }
        assertEquals(listOf(true, false), cached.map { it.monitored })
        assertEquals(true, library.albums.getValue(10).monitored)
    }

    @Test
    fun `applyAlbumStudio offline applies optimistically and queues the POST`() = runTest {
        library.artists[1] = libraryArtist(1)
        library.albums[10] = AlbumResource(id = 10, title = "First", foreignAlbumId = "mb-10", artistId = 1)
        seedCachedAlbum(library.albums.getValue(10))
        library.error = { if (it == "updateAlbumStudio") IOException("plane mode") else null }
        val payload = AlbumStudioResource(
            artist = listOf(
                dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioArtistResource(
                    id = 1,
                    albums = listOf(AlbumResource(id = 10, monitored = true)),
                ),
            ),
            monitoringOptions = MonitoringOptions(monitor = MonitorTypes.NONE),
        )
        val outcome = repository.applyAlbumStudio(payload)
        assertTrue(outcome is ArrResult.Ok && (outcome as ArrResult.Ok).value is AddOutcome.Queued)
        assertEquals("api/v1/albumstudio", queue.pending().single().path)
        assertEquals(
            true,
            store.getAll(serverId, LidarrAddNewRepository.ALBUM_TYPE, AlbumResource.serializer()).single().monitored,
        )
    }

    @Test
    fun `queuedCount counts only this server`() = runTest {
        assertEquals(0, repository.queuedCount().first())
        queue.enqueue(
            dev.appolon.homelab.arr.data.QueuedAction(
                actionId = dev.appolon.homelab.arr.core.ActionId("a1"),
                serverId = serverId,
                appType = dev.appolon.homelab.arr.core.AppType.LIDARR,
                method = "POST",
                path = "api/v1/artist",
                description = "test",
                enqueuedAtMillis = 1L,
            ),
        )
        assertEquals(1, repository.queuedCount().first())
    }
}
