package dev.appolon.homelab.arr.feature.radarr.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Navigation wiring for the Radarr movie detail feature. The main detail
 * screen fills the shell's built-in [ArrRoute.Detail] slot for RADARR; the
 * interactive import flow registers one nested pattern carrying the server
 * id (feature routes are server-agnostic by design).
 */
object RadarrDetailFeature {
    /** Owner key shared with the library feature's registrations. */
    const val ROUTE_OWNER = "radarr"

    /** Interactive import for one movie: `f/radarr/server/{id}/movie/{id}/import`. */
    const val IMPORT_PATTERN = "server/{serverId}/movie/{movieId}/import"

    val PATTERNS = listOf(IMPORT_PATTERN)

    /** Idempotent registration of every nested pattern this area owns. */
    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openImport(navigator: ArrShellNavigator, serverId: ServerId, movieId: Int) {
        val path = fillPattern(
            IMPORT_PATTERN,
            mapOf("serverId" to serverId.value, "movieId" to movieId.toString()),
        ) ?: return
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }
}

/**
 * Shell contribution: the RADARR detail slot and this feature's nested
 * routes. The integration layer merges it with the other features' slots.
 */
fun radarrDetailShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    detail = { route -> RadarrMovieDetailDestination(route, graph) },
    feature = { route -> RadarrDetailFeatureSlot(route, graph) },
)

/** Shell detail slot: renders the movie page for RADARR routes. */
@Composable
fun RadarrMovieDetailDestination(
    route: ArrRoute.Detail,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.app != AppType.RADARR) {
        ForeignFeature(route.app.name, modifier)
        return
    }
    val movieId = route.entityId.toIntOrNull()
    if (movieId == null || movieId <= 0) {
        EmptyState(
            title = stringResource(R.string.arr_radarr_detail_invalid_title),
            message = stringResource(R.string.arr_radarr_detail_invalid_message),
            modifier = modifier,
        )
        return
    }
    val repository = rememberRepository(graph, route.serverId)
    val navigator = LocalArrShellNavigator.current
    val vm: RadarrMovieDetailViewModel = viewModel(
        key = "radarr-movie-${route.serverId.value}-$movieId",
    ) {
        RadarrMovieDetailViewModel(
            serverId = route.serverId,
            movieId = movieId,
            repository = repository,
            queue = graph.actionQueue,
            imageClientSource = { repositoryImageClient(repository, route.serverId) },
        )
    }
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val messageText = detailMessageText(state.message)
    LaunchedEffect(state.message) {
        when {
            // Deleted (online) pops back to the caller; queued deletes keep the page.
            state.message == DetailMessage.Deleted -> {
                vm.consumeMessage()
                navigator.back()
            }
            messageText != null -> {
                snackbarHostState.showSnackbar(messageText)
                vm.consumeMessage()
            }
        }
    }
    androidx.compose.foundation.layout.Box(modifier) {
        RadarrMovieDetailScreen(
            state = state,
            onRetry = remember(vm) { { vm.load(false) } },
            onRefresh = vm::refresh,
            onBack = remember(navigator) { { navigator.back() } },
            onToggleMonitored = vm::toggleMonitored,
            onSaveEdit = vm::saveEdits,
            onDelete = vm::deleteMovie,
            onRename = vm::startRename,
            onSearch = vm::startSearch,
            onOpenImport = { RadarrDetailFeature.openImport(navigator, route.serverId, movieId) },
        )
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
    }
}

/** Nested feature slot: interactive import for this feature's routes. */
@Composable
fun RadarrDetailFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
) {
    if (route.owner != RadarrDetailFeature.ROUTE_OWNER) {
        ForeignFeature(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val params = matchRoutePattern(RadarrDetailFeature.IMPORT_PATTERN, segments)
    if (params == null) {
        ForeignFeature(route.owner, modifier)
        return
    }
    val serverId = params.getValue("serverId")
    val movieId = params.getValue("movieId").toIntOrNull()
    if (movieId == null || movieId <= 0) {
        EmptyState(
            title = stringResource(R.string.arr_radarr_detail_invalid_title),
            message = stringResource(R.string.arr_radarr_detail_invalid_message),
            modifier = modifier,
        )
        return
    }
    val repository = rememberRepository(graph, ServerId(serverId))
    val navigator = LocalArrShellNavigator.current
    val vm: RadarrManualImportViewModel = viewModel(
        key = "radarr-import-$serverId-$movieId",
    ) {
        RadarrManualImportViewModel(
            serverId = ServerId(serverId),
            movieId = movieId,
            repository = repository,
        )
    }
    val state by vm.state.collectAsState()
    RadarrManualImportScreen(
        state = state,
        onRetry = vm::refresh,
        onBack = { navigator.back() },
        onToggle = vm::toggle,
        onSelectAll = vm::selectAll,
        onRevalidate = vm::revalidate,
        onImport = vm::importSelected,
        onConsumeMessage = vm::consumeMessage,
        modifier = modifier,
    )
}

/** Placeholder for feature slots this module does not own. */
@Composable
private fun ForeignFeature(owner: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(
            ArrUiR.string.arr_shell_unavailable_title,
        ),
        message = stringResource(R.string.arr_radarr_detail_foreign_message, owner),
        modifier = modifier,
    )
}

/** One repository per graph; keyed so server edits rebuild it. */
@Composable
private fun rememberRepository(
    graph: ArrDataGraph,
    serverId: ServerId,
): RadarrMovieDetailRepository {
    val connection = remember(graph) {
        HttpRadarrDetailConnection(graph.registry, graph.secrets)
    }
    return remember(graph, serverId) {
        RadarrMovieDetailRepository(
            connection = connection,
            store = graph.entityStore,
            queue = graph.actionQueue,
        )
    }
}

private suspend fun repositoryImageClient(
    repository: RadarrMovieDetailRepository,
    serverId: ServerId,
): okhttp3.OkHttpClient? = repository.imageLoaderClient(serverId)

@Composable
private fun detailMessageText(message: DetailMessage?): String? = when (message) {
    null -> null
    DetailMessage.EditSaved -> stringResource(ArrUiR.string.arr_common_system_notice_saved)
    DetailMessage.EditQueued -> stringResource(R.string.arr_radarr_detail_msg_edit_queued)
    DetailMessage.RenameStarted -> stringResource(R.string.arr_radarr_detail_msg_rename_started)
    DetailMessage.RenameQueued -> stringResource(R.string.arr_radarr_detail_msg_rename_queued)
    DetailMessage.SearchStarted -> stringResource(ArrUiR.string.arr_common_detail_msg_search_started)
    DetailMessage.SearchQueued -> stringResource(R.string.arr_radarr_detail_msg_search_queued)
    DetailMessage.DeleteQueued -> stringResource(R.string.arr_radarr_detail_msg_delete_queued)
    DetailMessage.Deleted -> null
    is DetailMessage.WriteFailed ->
        dev.appolon.homelab.arr.ui.lce.arrErrorMessage(message.error)
}
