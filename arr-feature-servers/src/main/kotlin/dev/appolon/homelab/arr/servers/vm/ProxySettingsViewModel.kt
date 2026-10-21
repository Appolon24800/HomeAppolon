package dev.appolon.homelab.arr.servers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.registry.GlobalSettings
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.servers.logic.ProxyForm
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Proxy settings state: the global default proxy plus one override form per
 * server (a stored null proxy means "inherit the global"). The observed store
 * values always win over local edits: writes happen only on save, so an
 * emission is either this screen's own save echoing back or an external
 * registry change, and either way the stored state is the truth to show.
 */
class ProxySettingsViewModel(
    private val settings: GlobalSettings,
    private val registry: ServerRegistry,
) : ViewModel() {

    data class ServerProxyState(
        val server: ArrServer,
        val form: ProxyForm,
        val override: Boolean,
    )

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val global: ProxyForm = ProxyForm(),
        val servers: List<ServerProxyState> = emptyList(),
        val showErrors: Boolean = false,
        val saving: Boolean = false,
        val saveError: ArrError? = null,
        val savedTick: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<UiState>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                // The global editor stays meaningful with zero servers, so
                // there is no empty state for this screen.
                else -> LceState.Content(s, refreshing = s.saving)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            combine(registry.observe(), settings.observeDefaultProxy()) { servers, proxy ->
                servers to proxy
            }
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { (servers, defaultProxy) ->
                    _state.update { s ->
                        s.copy(
                            loaded = true,
                            global = ProxyForm.fromConfig(defaultProxy),
                            servers = servers.map { server ->
                                val storedForm = server.proxy
                                    ?.let(ProxyForm::fromConfig)
                                    ?: ProxyForm()
                                ServerProxyState(
                                    server = server,
                                    form = storedForm,
                                    override = server.proxy != null,
                                )
                            },
                        )
                    }
                }
        }
    }

    fun updateGlobal(form: ProxyForm) {
        _state.update { it.copy(global = form) }
    }

    /** Off means inherit the global default (stored as a null proxy). */
    fun setOverride(serverId: ServerId, override: Boolean) {
        mapRow(serverId) { it.copy(override = override) }
    }

    fun updateServer(serverId: ServerId, form: ProxyForm) {
        mapRow(serverId) { it.copy(form = form) }
    }

    /**
     * Persists the global default and every server's override, proxy, and
     * trust in one pass. Refuses to save (and flags the fields) when any
     * visible form still has problems.
     */
    fun save() {
        val current = _state.value
        val globalInvalid = current.global.problems.isNotEmpty()
        val serverInvalid = current.servers.any { it.override && it.form.problems.isNotEmpty() }
        if (globalInvalid || serverInvalid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                settings.setDefaultProxy(current.global.toProxyConfig())
                current.servers.forEach { row ->
                    registry.upsert(
                        row.server.copy(
                            proxy = if (row.override) row.form.toProxyConfig() else null,
                            trust = row.form.toTrustSpec(),
                        ),
                    )
                }
            }.onSuccess {
                _state.update { s ->
                    s.copy(saving = false, showErrors = false, savedTick = s.savedTick + 1)
                }
            }.onFailure { t ->
                _state.update { it.copy(saving = false, saveError = t.arrError()) }
            }
        }
    }

    private fun mapRow(serverId: ServerId, transform: (ServerProxyState) -> ServerProxyState) {
        _state.update { s ->
            s.copy(
                servers = s.servers.map { if (it.server.id == serverId) transform(it) else it },
            )
        }
    }
}
