package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient result of the webhook probe, surfaced as a snackbar. */
sealed interface NotificationsMessage {
    data object TestSucceeded : NotificationsMessage
    data class TestFailed(val error: ArrError) : NotificationsMessage
}

/** State of the notifications screen. */
data class NotificationsUiState(
    val testing: Boolean = false,
    val message: NotificationsMessage? = null,
)

/**
 * ViewModel of the notifications screen. Bazarr's webhook surface in the
 * bundled spec is inbound-only (Plex, Radarr, Sonarr triggers) plus one
 * outbound probe, so the screen has no cached list; it renders the channel
 * matrix and runs the connection test on demand.
 */
class NotificationsViewModel(
    private val repository: BazarrSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<NotificationsUiState>> = state
        .map { s -> LceState.Content(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    /**
     * Tests the external webhook connection. A diagnostic probe describes
     * the server right now, so failures surface immediately and never queue.
     */
    fun testWebhook() {
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            val message = when (val result = repository.testWebhook()) {
                is ArrResult.Ok -> NotificationsMessage.TestSucceeded
                is ArrResult.Err -> NotificationsMessage.TestFailed(result.error)
            }
            _state.update { it.copy(testing = false, message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
