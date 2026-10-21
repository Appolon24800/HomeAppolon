package dev.appolon.homelab.arr.feature.lidarr.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.lidarr.detail.data.LidarrArtistDetailRepository
import dev.appolon.homelab.arr.feature.lidarr.detail.ui.ArtistDetailContent
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.ArtistDetailViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/** Everything the artist detail screen needs from the app's singletons. */
class LidarrArtistDetailDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = LidarrArtistDetailDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's detail slot for Lidarr. The shell already owns the
 * `server/{serverId}/lidarr/detail/{entityId}` route; this wiring renders it
 * and leaves every other app's detail slot untouched so sibling feature
 * modules can wrap the same content object.
 */
fun ArrShellContent.withLidarrArtistDetail(
    deps: LidarrArtistDetailDeps,
): ArrShellContent {
    val previousDetail = detail
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = { route ->
            if (route.app == AppType.LIDARR) {
                val artistId = route.entityId.toIntOrNull()
                if (artistId != null) {
                    LidarrArtistDetail(
                        serverId = route.serverId,
                        artistId = artistId,
                        deps = deps,
                    )
                } else {
                    previousDetail?.invoke(route)
                }
            } else {
                previousDetail?.invoke(route)
            }
        },
        add = add,
        queue = queue,
        calendar = calendar,
        settings = settings,
        feature = feature,
    )
}

/**
 * Stateful entry: connects to the server, builds the repository over the
 * entity store and action queue, then renders the detail screen. The
 * connection gate keeps the ViewModel constructor synchronous and testable.
 */
@Composable
fun LidarrArtistDetail(
    serverId: ServerId,
    artistId: Int,
    deps: LidarrArtistDetailDeps,
) {
    var apis by remember(serverId) { mutableStateOf<LidarrArtistDetailApis?>(null) }
    LaunchedEffect(serverId) {
        apis = LidarrArtistDetailConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            clientFactory = deps.clientFactory,
        ).connect(serverId)
    }
    val connected = apis
    if (connected == null) {
        ArrLceBox(state = LceState.Loading, modifier = Modifier.fillMaxSize()) {}
        return
    }
    val repository = remember(serverId, connected) {
        LidarrArtistDetailRepository(
            serverId = serverId,
            store = deps.entityStore,
            artistApi = connected.artistApi,
            albumApi = connected.albumApi,
            trackApi = connected.trackApi,
            trackFileApi = connected.trackFileApi,
            renameTrackApi = connected.renameTrackApi,
            retagTrackApi = connected.retagTrackApi,
            historyApi = connected.historyApi,
            releaseApi = connected.releaseApi,
            manualImportApi = connected.manualImportApi,
            qualityProfileApi = connected.qualityProfiles,
            qualityDefinitionApi = connected.qualityDefinitions,
            metadataProfileApi = connected.metadataProfiles,
            tagApi = connected.tags,
            commandPoster = connected.commandPoster,
            actionQueue = deps.actionQueue,
        )
    }
    val vm: ArtistDetailViewModel = viewModel(
        key = "lidarr-artist-detail-$artistId-" + serverId.value,
        factory = viewModelFactory {
            initializer { ArtistDetailViewModel(artistId, repository) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = remember(connected) { connected.imageLoader(context) }

    // Leaving after a confirmed delete: the cache row is gone on both the
    // synced and queued paths, so the library (or parent) resumes.
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            navigator.back()
        }
    }

    ArtistDetailContent(
        state = state,
        baseUrl = connected.baseUrl,
        imageLoader = imageLoader,
        onBack = { navigator.back() },
        onRefresh = vm::refresh,
        onSetTab = vm::setTab,
        onSelectAlbum = vm::selectAlbum,
        onToggleArtistMonitored = vm::toggleArtistMonitored,
        onToggleAlbumMonitored = vm::toggleAlbumMonitored,
        onRefreshMetadata = vm::refreshMetadata,
        onScanDisk = vm::scanDisk,
        onSearchArtist = vm::searchArtist,
        onSearchAlbum = vm::searchAlbum,
        onOpenRenamePreview = vm::openRenamePreview,
        onCloseRenamePreview = vm::closeRenamePreview,
        onRunRename = vm::runRename,
        onOpenRetagPreview = vm::openRetagPreview,
        onCloseRetagPreview = vm::closeRetagPreview,
        onRunRetag = vm::runRetag,
        onOpenImport = vm::openImport,
        onCloseImport = vm::closeImport,
        onToggleImportSelected = vm::toggleImportSelected,
        onImportSelected = vm::importSelected,
        onOpenManualSearchArtist = vm::openManualSearchArtist,
        onOpenManualSearchAlbum = vm::openManualSearchAlbum,
        onCloseManualSearch = vm::closeManualSearch,
        onRefreshReleases = vm::refreshReleases,
        onGrabRelease = vm::grabRelease,
        onOpenFileEditor = vm::openFileEditor,
        onCloseFileEditor = vm::closeFileEditor,
        onUpdateFileEditor = { form -> vm.updateFileEditor { form } },
        onSubmitFileEditor = vm::submitFileEditor,
        onDeleteFileFromEditor = vm::deleteFileFromEditor,
        onOpenEdit = vm::openEdit,
        onCloseEdit = vm::closeEdit,
        onUpdateEdit = { form -> vm.updateEdit { form } },
        onSubmitEdit = vm::submitEdit,
        onRequestDelete = vm::requestDelete,
        onDismissDelete = vm::dismissDelete,
        onConfirmDelete = vm::confirmDelete,
        onRefreshHistory = vm::refreshHistory,
        onConsumeMessage = vm::consumeMessage,
    )
}
