package dev.appolon.homelab.arr.feature.prowlarr.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrIndexersApis
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrSearchApis
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrSettingsApis
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Registers this group's routes without claiming sibling feature owners. */
object ProwlarrSearchFeature {
    const val ROUTE_OWNER = "prowlarr-search"
    const val SEARCH_PATTERN = "search/{serverId}"
    const val HISTORY_PATTERN = "search-history/{serverId}"
    const val STATS_PATTERN = "search-stats/{serverId}"

    fun register(registry: ArrRouteRegistry) {
        listOf(SEARCH_PATTERN, HISTORY_PATTERN, STATS_PATTERN).forEach {
            registry.register(ROUTE_OWNER, it)
        }
    }

    fun route(serverId: ServerId, page: SearchPage = SearchPage.SEARCH): ArrRoute.Feature? {
        val pattern = when (page) {
            SearchPage.SEARCH -> SEARCH_PATTERN
            SearchPage.HISTORY -> HISTORY_PATTERN
            SearchPage.STATS -> STATS_PATTERN
        }
        return fillPattern(pattern, mapOf("serverId" to serverId.value))?.let {
            ArrRoute.Feature(ROUTE_OWNER, it)
        }
    }
}

/** Slot contribution consumed by the app's arr integration. */
fun prowlarrSearchShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    feature = { route -> ProwlarrSearchFeatureSlot(route, graph) },
)

@Composable
fun ProwlarrSearchFeatureSlot(route: ArrRoute.Feature, graph: ArrDataGraph) {
    if (route.owner != ProwlarrSearchFeature.ROUTE_OWNER) return
    val match = listOf(
        ProwlarrSearchFeature.SEARCH_PATTERN to SearchPage.SEARCH,
        ProwlarrSearchFeature.HISTORY_PATTERN to SearchPage.HISTORY,
        ProwlarrSearchFeature.STATS_PATTERN to SearchPage.STATS,
    ).firstNotNullOfOrNull { (pattern, page) ->
        matchRoutePattern(pattern, route.path.split('/'))?.let { it to page }
    } ?: return
    val serverId = ServerId(match.first.getValue("serverId"))
    val connection = remember(graph, serverId) { ProwlarrSearchConnection(graph) }
    var repository by remember(graph, serverId) {
        mutableStateOf<LceState<ProwlarrSearchRepository>>(LceState.Loading)
    }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(graph, serverId, attempt) {
        repository = LceState.Loading
        repository = LceState.fromResult(connection.repository(serverId))
    }
    ArrLceBox(
        state = repository, onRetry = { attempt++ },
        emptyTitle = stringResource(R.string.prowlarr_search_title),
        emptyMessage = stringResource(R.string.prowlarr_search_connection),
    ) { resolved ->
        val vm: ProwlarrSearchViewModel = viewModel(key = "prowlarr-search-${serverId.value}") {
            ProwlarrSearchViewModel(resolved)
        }
        val state by vm.state.collectAsState()
        val navigator = LocalArrShellNavigator.current
        LaunchedEffect(match.second) {
            when (match.second) {
                SearchPage.SEARCH -> Unit
                SearchPage.HISTORY -> vm.loadHistory()
                SearchPage.STATS -> vm.loadStats()
            }
        }
        ProwlarrSearchScreen(
            state, match.second, vm,
            onPage = { page -> ProwlarrSearchFeature.route(serverId, page)?.let(navigator::open) },
            onBack = { navigator.back() },
        )
    }
}

/** Uses the shared proxy, trust, authentication and base-path handling. */
class ProwlarrSearchConnection(private val graph: ArrDataGraph) {
    suspend fun repository(serverId: ServerId): ArrResult<ProwlarrSearchRepository> =
        withContext(Dispatchers.IO) {
            try {
                val server = graph.registry.byId(serverId)
                    ?: return@withContext ArrResult.err(ArrError.Auth(401))
                val endpoint = ServerEndpoint(
                    serverId = serverId,
                    baseUrl = server.baseUrl,
                    apiKey = graph.secrets.decrypt(server.apiKeyEncrypted),
                    basicAuthUser = server.basicAuthUser,
                    basicAuthPassword = server.basicAuthPasswordEncrypted?.let {
                        graph.secrets.decrypt(it)
                    },
                    proxy = server.proxy,
                    trust = server.trust ?: TrustSpec.System,
                )
                val retrofit = Retrofit.Builder()
                    .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
                    .client(DefaultArrClientFactory().clientFor(endpoint))
                    .addConverterFactory(
                        ArrJson.asConverterFactory("application/json".toMediaType()),
                    )
                    .build()
                ArrResult.ok(ProwlarrSearchRepository(
                    serverId, graph.entityStore, graph.actionQueue,
                    ProwlarrSearchApis.search(retrofit), ProwlarrSearchApis.history(retrofit),
                    ProwlarrSearchApis.indexerStats(retrofit),
                    ProwlarrIndexersApis.indexer(retrofit),
                    ProwlarrIndexersApis.indexerDefaultCategories(retrofit),
                    ProwlarrSettingsApis.downloadClient(retrofit),
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ArrResult.err(e.arrError())
            }
        }
}
