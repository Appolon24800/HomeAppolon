package dev.appolon.homelab.arr.feature.sonarr.detail

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
import dev.appolon.homelab.arr.feature.sonarr.detail.ui.SeriesDetailContent
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.SeriesDetailViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/** Everything the series detail screen needs from the app's singletons. */
class SonarrSeriesDetailDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = SonarrSeriesDetailDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's detail slot for Sonarr. The shell already owns the
 * `server/{serverId}/sonarr/detail/{entityId}` route; this wiring renders it
 * and leaves every other app's detail slot untouched so sibling feature
 * modules can wrap the same content object.
 */
fun ArrShellContent.withSonarrSeriesDetail(
    deps: SonarrSeriesDetailDeps,
): ArrShellContent {
    val previousDetail = detail
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = { route ->
            if (route.app == AppType.SONARR) {
                val seriesId = route.entityId.toIntOrNull()
                if (seriesId != null) {
                    SonarrSeriesDetail(
                        serverId = route.serverId,
                        seriesId = seriesId,
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
fun SonarrSeriesDetail(
    serverId: ServerId,
    seriesId: Int,
    deps: SonarrSeriesDetailDeps,
) {
    var apis by remember(serverId) { mutableStateOf<SonarrSeriesDetailApis?>(null) }
    LaunchedEffect(serverId) {
        apis = SonarrSeriesDetailConnection(
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
        dev.appolon.homelab.arr.feature.sonarr.detail.data.SonarrSeriesDetailRepository(
            serverId = serverId,
            store = deps.entityStore,
            seriesApi = connected.seriesApi,
            episodeApi = connected.episodeApi,
            episodeFileApi = connected.episodeFileApi,
            renameEpisodeApi = connected.renameEpisodeApi,
            seasonPassApi = connected.seasonPassApi,
            historyApi = connected.historyApi,
            releaseApi = connected.releaseApi,
            manualImportApi = connected.manualImportApi,
            commandPoster = connected.commandPoster,
            qualityProfileApi = connected.qualityProfiles,
            qualityDefinitionApi = connected.qualityDefinitions,
            languageApi = connected.languages,
            tagApi = connected.tags,
            actionQueue = deps.actionQueue,
        )
    }
    val vm: SeriesDetailViewModel = viewModel(
        key = "sonarr-series-detail-$seriesId-" + serverId.value,
        factory = viewModelFactory {
            initializer { SeriesDetailViewModel(seriesId, repository) }
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

    SeriesDetailContent(
        state = state,
        baseUrl = connected.baseUrl,
        imageLoader = imageLoader,
        onBack = remember(navigator) { { navigator.back() } },
        onRefresh = vm::refresh,
        onSetTab = vm::setTab,
        onSelectSeason = vm::selectSeason,
        onToggleSeriesMonitored = vm::toggleSeriesMonitored,
        onToggleSeasonMonitored = vm::toggleSeasonMonitored,
        onToggleEpisodeMonitored = vm::toggleEpisodeMonitored,
        onSearchSeries = vm::searchSeries,
        onSearchSeason = vm::searchSeason,
        onSearchEpisode = vm::searchEpisode,
        onRefreshMetadata = vm::refreshMetadata,
        onScanDisk = vm::scanDisk,
        onOpenRenamePreview = vm::openRenamePreview,
        onCloseRenamePreview = vm::closeRenamePreview,
        onRunRename = vm::runRename,
        onOpenImport = vm::openImport,
        onCloseImport = vm::closeImport,
        onToggleImportSelected = vm::toggleImportSelected,
        onImportSelected = vm::importSelected,
        onOpenManualSearchEpisode = vm::openManualSearchEpisode,
        onOpenManualSearchSeason = vm::openManualSearchSeason,
        onCloseManualSearch = vm::closeManualSearch,
        onRefreshReleases = vm::refreshReleases,
        onGrabRelease = vm::grabRelease,
        onOpenFileEditor = vm::openFileEditor,
        onCloseFileEditor = vm::closeFileEditor,
        onUpdateFileEditor = remember(vm) { { form -> vm.updateFileEditor { form } } },
        onSubmitFileEditor = vm::submitFileEditor,
        onDeleteFileFromEditor = vm::deleteFileFromEditor,
        onOpenEdit = vm::openEdit,
        onCloseEdit = vm::closeEdit,
        onUpdateEdit = remember(vm) { { form -> vm.updateEdit { form } } },
        onSubmitEdit = vm::submitEdit,
        onOpenSeasonPass = vm::openSeasonPass,
        onCloseSeasonPass = vm::closeSeasonPass,
        onUpdateSeasonPass = remember(vm) { { form -> vm.updateSeasonPass { form } } },
        onSubmitSeasonPass = vm::submitSeasonPass,
        onRequestDelete = vm::requestDelete,
        onDismissDelete = vm::dismissDelete,
        onConfirmDelete = vm::confirmDelete,
        onRefreshHistory = vm::refreshHistory,
        onConsumeMessage = vm::consumeMessage,
    )
}
