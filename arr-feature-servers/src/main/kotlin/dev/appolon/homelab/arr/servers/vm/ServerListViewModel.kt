package dev.appolon.homelab.arr.servers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.servers.net.ConnectionOutcome
import dev.appolon.homelab.arr.servers.net.ConnectionTester
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

/**
 * Server list state: registry rows plus a health dot per server, probed with
 * a status-endpoint GET. Registry reads are local, so the list itself is
 * offline-tolerant; only the health probe needs the network and its failures
 * degrade to "unreachable" rather than an error pane.
 */
class ServerListViewModel(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val tester: ConnectionTester,
    private val deleteEverywhere: suspend (ServerId) -> Unit = {},
) : ViewModel() {

    /** Reachability of one server as shown by the dot on its card. */
    enum class Health { OK, UNREACHABLE, UNKNOWN }

    data class UiState(
        val servers: List<ArrServer> = emptyList(),
        val health: Map<ServerId, Health> = emptyMap(),
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val probing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<List<ArrServer>>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.servers.isEmpty() -> LceState.Empty
                else -> LceState.Content(s.servers, refreshing = s.probing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            registry.observe()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { servers ->
                    _state.update { s ->
                        s.copy(
                            servers = servers,
                            loaded = true,
                            health = s.health.filterKeys { id -> servers.any { it.id == id } },
                        )
                    }
                    if (servers.isNotEmpty()) probeAll()
                }
        }
    }

    /** Re-probes every configured server; a save or registry change triggers it too. */
    fun probeAll() {
        viewModelScope.launch {
            val servers = _state.value.servers
            if (servers.isEmpty()) return@launch
            _state.update { it.copy(probing = true) }
            val results = servers.associate { server -> server.id to probe(server) }
            _state.update { s -> s.copy(probing = false, health = s.health + results) }
        }
    }

    fun moveUp(id: ServerId) = move(id, -1)

    fun moveDown(id: ServerId) = move(id, 1)

    fun delete(id: ServerId) {
        viewModelScope.launch { deleteEverywhere(id) }
    }

    private suspend fun probe(server: ArrServer): Health {
        val endpoint = endpointOf(server) ?: return Health.UNKNOWN
        return when (tester.test(endpoint, server.type)) {
            is ConnectionOutcome.Success -> Health.OK
            is ConnectionOutcome.Failure -> Health.UNREACHABLE
        }
    }

    private fun move(id: ServerId, offset: Int) {
        viewModelScope.launch {
            val ids = _state.value.servers.map { it.id }
            val index = ids.indexOf(id)
            val target = index + offset
            if (index < 0 || target !in ids.indices) return@launch
            val reordered = ids.toMutableList().apply { add(target, removeAt(index)) }
            registry.reorder(reordered)
        }
    }

    /** Null when stored secrets cannot be decrypted; the card then shows an unknown dot. */
    private fun endpointOf(server: ArrServer): ServerEndpoint? = try {
        ServerEndpoint(
            serverId = server.id,
            baseUrl = server.baseUrl,
            apiKey = secrets.decrypt(server.apiKeyEncrypted),
            basicAuthUser = server.basicAuthUser,
            basicAuthPassword = server.basicAuthPasswordEncrypted?.let { secrets.decrypt(it) },
            proxy = server.proxy,
            trust = server.trust ?: TrustSpec.System,
        )
    } catch (t: Throwable) {
        null
    }
}
