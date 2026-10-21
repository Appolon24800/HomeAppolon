package dev.appolon.homelab.arr.feature.lidarr.addnew.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrDiscovery
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.addnew.FakeActionQueue
import dev.appolon.homelab.arr.feature.lidarr.addnew.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.LidarrAddNewRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddNewViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private val serverId = ServerId("s1")

    private lateinit var library: FakeLidarrLibrary
    private lateinit var system: FakeLidarrSystem
    private lateinit var calendar: FakeLidarrCalendar
    private lateinit var discovery: FakeLidarrDiscovery
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: LidarrAddNewRepository
    private lateinit var vm: AddNewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): AddNewViewModel {
        library = FakeLidarrLibrary()
        system = FakeLidarrSystem()
        calendar = FakeLidarrCalendar()
        discovery = FakeLidarrDiscovery()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        library.artists[1] = ArtistResource(
            id = 1,
            artistName = "Aurora",
            foreignArtistId = "mb-aurora",
            statistics = dev.appolon.homelab.arr.api.lidarr.model.ArtistStatisticsResource(albumCount = 2),
        )
        library.albums[10] = AlbumResource(
            id = 10,
            title = "First",
            foreignAlbumId = "mb-aurora-first",
            artistId = 1,
            albumType = "Album",
            releaseDate = "2018-01-01",
        )
        library.albums[11] = AlbumResource(
            id = 11,
            title = "Second",
            foreignAlbumId = "mb-aurora-second",
            artistId = 1,
            albumType = "Album",
            releaseDate = "2022-01-01",
        )
        system.rootFolders[1] = RootFolderResource(
            id = 1,
            path = "/media/music",
            freeSpace = 1_000L,
            defaultQualityProfileId = 2,
        )
        system.rootFolders[2] = RootFolderResource(id = 2, path = "/media/lossless", freeSpace = 2_000L)
        calendar.qualityProfiles[1] = QualityProfileResource(id = 1, name = "Standard")
        calendar.qualityProfiles[2] = QualityProfileResource(id = 2, name = "Lossless")
        calendar.metadataProfiles[1] = MetadataProfileResource(id = 1, name = "Standard")
        system.tags[4] = TagResource(id = 4, label = "vinyl")
        discovery.importLists[1] = ImportListResource(
            id = 1,
            name = "Spotify saved",
            implementationName = "Spotify Playlist",
            enableAutomaticAdd = true,
            qualityProfileId = 1,
        )
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
            io = main,
        )
        return AddNewViewModel(repository)
    }

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        vm = newVm()
        val job = launch { vm.state.collect {} }
        advanceUntilIdle()
        block()
        job.cancel()
    }

    @Test
    fun `init loads catalogs, library, and import lists`() = runVm {
        val state = vm.state.value
        assertEquals(listOf("/media/music", "/media/lossless"), state.rootFolders.map { it.path })
        assertEquals(listOf("Standard", "Lossless"), state.qualityProfiles.map { it.name })
        assertEquals(listOf("Standard"), state.metadataProfiles.map { it.name })
        assertEquals(listOf("vinyl"), state.tags.map { it.label })
        assertTrue(state.existingForeignArtistIds.contains("mb-aurora"))
        assertEquals(listOf("Aurora"), state.libraryArtists.map { it.artistName })
        val lists = state.listsLce
        assertTrue(lists is LceState.Content)
        assertEquals(1, (lists as LceState.Content).data.size)
        assertEquals(listOf("Aurora"), (state.studioLce as LceState.Content).data.map { it.artistName })
    }

    @Test
    fun `artist search fills the artist results and badges existing matches`() = runVm {
        vm.onQueryChange("Aur")
        vm.submitSearch()
        val state = vm.state.value
        assertTrue(state.searchActive)
        val content = state.searchLce
        assertTrue(content is LceState.Content)
        val artists = (content as LceState.Content).data.artists
        assertEquals(listOf("Aurora"), artists.map { it.artistName })
        // The lookup result carries the library's MusicBrainz id, so the
        // screen shows the in-library chip instead of an add button.
        assertTrue("mb-aurora" in state.existingForeignArtistIds)
    }

    @Test
    fun `album scope switch re-runs the search against the album catalog`() = runVm {
        vm.onQueryChange("First")
        vm.submitSearch()
        // No artist matches "First"; the artist-scope result is empty.
        assertTrue(vm.state.value.searchLce is LceState.Empty)
        vm.setScope(SearchScope.ALBUM)
        val state = vm.state.value
        assertEquals(SearchScope.ALBUM, state.scope)
        val albums = (state.searchLce as LceState.Content).data.albums
        assertEquals(listOf("First"), albums.map { it.title })
    }

    @Test
    fun `blank query resets the results area to the tabs`() = runVm {
        vm.onQueryChange("Aur")
        vm.submitSearch()
        assertTrue(vm.state.value.searched)
        vm.onQueryChange("")
        val state = vm.state.value
        assertFalse(state.searchActive)
        assertFalse(state.searched)
        assertNull(state.searchLce)
    }

    @Test
    fun `openArtistAddSheet preselects the folder default profile`() = runVm {
        vm.openArtistAddSheet(
            ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"),
        )
        val sheet = vm.state.value.artistSheet
        assertEquals("/media/music", sheet?.rootFolderPath)
        assertEquals(2, sheet?.qualityProfileId)
        assertEquals(1, sheet?.metadataProfileId)
        assertTrue(sheet?.submittable == true)
    }

    @Test
    fun `submitArtistAdd posts the payload and reports the add`() = runVm {
        vm.openArtistAddSheet(
            ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"),
        )
        vm.updateArtistAddSheet { it.copy(selectedTags = setOf(4), monitor = MonitorTypes.FUTURE) }
        vm.submitArtistAdd(searchForMissingAlbums = false)
        val state = vm.state.value
        assertNull(state.artistSheet)
        assertEquals(AddNewMessage.ArtistAdded("New Band"), state.message)
        val added = library.artists.values.first { it.foreignArtistId == "mb-new" }
        assertEquals(listOf(4), added.tags)
        assertEquals(MonitorTypes.FUTURE, added.addOptions?.monitor)
        assertEquals(false, added.addOptions?.searchForMissingAlbums)
    }

    @Test
    fun `submitArtistAdd with search reports the search`() = runVm {
        vm.openArtistAddSheet(
            ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"),
        )
        vm.submitArtistAdd(searchForMissingAlbums = true)
        assertEquals(
            AddNewMessage.ArtistAddedAndSearching("New Band"),
            vm.state.value.message,
        )
        assertEquals(
            true,
            library.artists.values.first { it.foreignArtistId == "mb-new" }
                .addOptions?.searchForMissingAlbums,
        )
    }

    @Test
    fun `submitArtistAdd offline queues and flags the artist`() = runVm {
        library.error = { IOException("plane mode") }
        vm.openArtistAddSheet(
            ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"),
        )
        vm.submitArtistAdd(searchForMissingAlbums = false)
        val state = vm.state.value
        assertEquals(AddNewMessage.ArtistAddQueued("New Band"), state.message)
        assertNull(state.artistSheet)
        assertEquals(setOf("mb-new"), state.queuedForeignArtistIds)
        assertEquals(1, state.pendingCount)
        // The optimistic row makes the result show as existing-in-library.
        assertTrue(state.existingForeignArtistIds.contains("mb-new"))
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `openAlbumAddSheet resolves the library artist from the candidate`() = runVm {
        vm.openAlbumAddSheet(
            AlbumResource(
                title = "Third",
                foreignAlbumId = "mb-aurora-third",
                artist = ArtistResource(id = 1, artistName = "Aurora", foreignArtistId = "mb-aurora"),
            ),
        )
        val sheet = vm.state.value.albumSheet
        assertEquals(1, sheet?.libraryArtist?.id)
        assertTrue(sheet?.submittable == true)
    }

    @Test
    fun `album add stays blocked until the artist is in the library`() = runVm {
        vm.openAlbumAddSheet(
            AlbumResource(
                title = "Third",
                foreignAlbumId = "mb-other-third",
                artist = ArtistResource(id = 0, artistName = "Other Band", foreignArtistId = "mb-other"),
            ),
        )
        assertFalse(vm.state.value.albumSheet?.submittable == true)
    }

    @Test
    fun `submitAlbumAdd posts under the resolved artist`() = runVm {
        vm.openAlbumAddSheet(
            AlbumResource(
                title = "Third",
                foreignAlbumId = "mb-aurora-third",
                artist = ArtistResource(id = 1, artistName = "Aurora", foreignArtistId = "mb-aurora"),
            ),
        )
        vm.updateAlbumAddSheet { it.copy(monitored = false) }
        vm.submitAlbumAdd(searchForNewAlbum = true)
        val state = vm.state.value
        assertNull(state.albumSheet)
        assertEquals(AddNewMessage.AlbumAddedAndSearching("Third"), state.message)
        val added = library.albums.values.first { it.foreignAlbumId == "mb-aurora-third" }
        assertEquals(1, added.artistId)
        assertEquals(false, added.monitored)
        assertEquals(true, added.addOptions?.searchForNewAlbum)
    }

    @Test
    fun `submitAlbumAdd offline queues the add`() = runVm {
        library.error = { IOException("plane mode") }
        vm.openAlbumAddSheet(
            AlbumResource(
                title = "Third",
                foreignAlbumId = "mb-aurora-third",
                artist = ArtistResource(id = 1, artistName = "Aurora", foreignArtistId = "mb-aurora"),
            ),
        )
        vm.submitAlbumAdd(searchForNewAlbum = false)
        val state = vm.state.value
        assertEquals(AddNewMessage.AlbumAddQueued("Third"), state.message)
        assertEquals(setOf("mb-aurora-third"), state.queuedForeignAlbumIds)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `studio picker loads the discography and applies the selection`() = runVm {
        val artist = library.artists.getValue(1)
        vm.openStudioPicker(artist)
        val loaded = vm.state.value.studioPicker
        assertEquals(listOf("Second", "First"), loaded?.albums?.map { it.title })
        vm.setStudioStrategy(MonitorTypes.ALL)
        vm.toggleStudioAlbum(11)
        val picker = vm.state.value.studioPicker
        // ALL preselects both, the toggle flips album 11 off: one change sent.
        assertEquals(1, picker?.changedCount)
        vm.submitStudio()
        val state = vm.state.value
        assertNull(state.studioPicker)
        assertEquals(AddNewMessage.StudioApplied("Aurora", 1), state.message)
        assertEquals(true, library.albums.getValue(10).monitored)
        assertEquals(false, library.albums.getValue(11).monitored)
    }

    @Test
    fun `studio picker preselect keeps current state under NONE`() = runVm {
        vm.openStudioPicker(library.artists.getValue(1))
        val picker = vm.state.value.studioPicker
        assertEquals(MonitorTypes.NONE, picker?.strategy)
        assertEquals(0, picker?.changedCount)
    }

    @Test
    fun `studio submit offline queues and keeps the optimistic state`() = runVm {
        library.error = { if (it == "updateAlbumStudio") IOException("plane mode") else null }
        vm.openStudioPicker(library.artists.getValue(1))
        vm.setStudioStrategy(MonitorTypes.ALL)
        vm.submitStudio()
        val state = vm.state.value
        assertEquals(AddNewMessage.StudioQueued("Aurora"), state.message)
        assertEquals(1, queue.pending().size)
        // The cache carries the optimistic monitored flags.
        assertEquals(
            true,
            store.getAll(serverId, LidarrAddNewRepository.ALBUM_TYPE, AlbumResource.serializer())
                .first { it.id == 10 }
                .monitored,
        )
    }

    @Test
    fun `studio select all and none set every target`() = runVm {
        vm.openStudioPicker(library.artists.getValue(1))
        vm.studioSelectNone()
        assertEquals(mapOf(10 to false, 11 to false), vm.state.value.studioPicker?.selection)
        vm.studioSelectAll()
        assertEquals(mapOf(10 to true, 11 to true), vm.state.value.studioPicker?.selection)
    }

    @Test
    fun `consumeMessage clears the snackbar state`() = runVm {
        vm.openArtistAddSheet(ArtistResource(artistName = "New Band", foreignArtistId = "mb-new"))
        vm.submitArtistAdd(searchForMissingAlbums = false)
        assertTrue(vm.state.value.message != null)
        vm.consumeMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `setTab switches tabs and refreshLists reloads the feed`() = runVm {
        vm.setTab(AddNewTab.LISTS)
        assertEquals(AddNewTab.LISTS, vm.state.value.tab)
        discovery.importLists.clear()
        vm.refreshLists()
        val lists = vm.state.value.listsLce
        // The cleared feed falls back to the cached copy; the cache survives.
        assertTrue(lists is LceState.Content)
        assertEquals(1, (lists as LceState.Content).data.size)
    }
}
