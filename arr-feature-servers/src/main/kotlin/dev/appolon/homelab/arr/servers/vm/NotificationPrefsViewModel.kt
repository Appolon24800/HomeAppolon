package dev.appolon.homelab.arr.servers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.registry.GlobalSettings
import dev.appolon.homelab.arr.data.registry.NotificationChannels
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.servers.data.ServersSettingsStore
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
 * Notification preferences: the global kill switch (module-level, since
 * GlobalSettings has no key for it) plus the five per-server event channels
 * persisted through GlobalSettings. All writes are local preference writes,
 * so the screen works fully offline.
 */
class NotificationPrefsViewModel(
    private val settings: GlobalSettings,
    private val prefs: ServersSettingsStore,
    registry: ServerRegistry,
) : ViewModel() {

    /** Event categories :arr-sync emits; one toggle each. */
    enum class Kind { GRAB, IMPORT, FAIL, HEALTH, DISK }

    data class ServerNotificationState(
        val server: ArrServer,
        val channels: NotificationChannels,
    )

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val killSwitch: Boolean = true,
        val servers: List<ServerNotificationState> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<List<ServerNotificationState>>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.servers.isEmpty() -> LceState.Empty
                else -> LceState.Content(s.servers)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            combine(registry.observe(), prefs.observeNotificationsEnabled()) { servers, kill ->
                servers to kill
            }
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { (servers, killSwitch) ->
                    val rows = servers.map {
                        ServerNotificationState(it, settings.notificationChannels(it.id))
                    }
                    _state.update { s ->
                        s.copy(
                            loaded = true,
                            killSwitch = killSwitch,
                            servers = rows,
                            loadError = null,
                        )
                    }
                }
        }
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { prefs.setNotificationsEnabled(enabled) }
                .onSuccess { _state.update { it.copy(killSwitch = enabled) } }
                .onFailure { t -> _state.update { it.copy(loadError = t.arrError()) } }
        }
    }

    fun setEnabled(serverId: ServerId, kind: Kind, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                val current = settings.notificationChannels(serverId)
                current.with(kind, enabled).also { updated ->
                    settings.setNotificationChannels(serverId, updated)
                }
            }.onSuccess { channels ->
                _state.update { s ->
                    s.copy(
                        servers = s.servers.map {
                            if (it.server.id == serverId) it.copy(channels = channels) else it
                        },
                    )
                }
            }.onFailure { t -> _state.update { it.copy(loadError = t.arrError()) } }
        }
    }

    private fun NotificationChannels.with(kind: Kind, enabled: Boolean): NotificationChannels =
        when (kind) {
            Kind.GRAB -> copy(grab = enabled)
            Kind.IMPORT -> copy(import = enabled)
            Kind.FAIL -> copy(fail = enabled)
            Kind.HEALTH -> copy(health = enabled)
            Kind.DISK -> copy(disk = enabled)
        }
}
