package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the notifications LCE state. */
data class RadarrNotificationsContent(
    val notifications: List<NotificationResource>,
    val tags: List<TagResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
    val testResults: Map<Int, ProviderTestAllResult>,
)

/** Transient results of notification actions, mapped to strings by the screen. */
enum class NotificationsNotice {
    DELETED,
    DELETED_QUEUED,
    TEST_ALL_DONE,
}

/**
 * Connect notifications list state: cached rows summarizing the enabled
 * event toggles, test-all with per-connection verdicts, and queued badges
 * for pending removals.
 */
class RadarrNotificationsViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val notifications: List<NotificationResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val testResults: Map<Int, ProviderTestAllResult> = emptyMap(),
        val testingAll: Boolean = false,
        val confirmDeleteFor: NotificationResource? = null,
        val writeError: ArrError? = null,
        val notice: NotificationsNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrNotificationsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.notifications.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.notifications.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrNotificationsContent(
                        notifications = s.notifications,
                        tags = s.tags,
                        queuedRemovedIds = s.queuedRemovedIds,
                        pendingActions = s.pendingActions,
                        testResults = s.testResults,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeNotifications().collect { notifications ->
                _state.update {
                    it.copy(loaded = true, loadError = null, notifications = notifications)
                }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(RadarrConnectRepository.NOTIFICATION_PATH).collect { ids ->
                _state.update { it.copy(queuedRemovedIds = ids) }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.notifications.isEmpty()) result.error else null,
                        writeError = if (it.notifications.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun testAll() {
        viewModelScope.launch {
            _state.update { it.copy(testingAll = true) }
            when (val result = repository.testAllNotifications()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        testingAll = false,
                        testResults = result.value.associateBy { verdict -> verdict.id },
                        notice = NotificationsNotice.TEST_ALL_DONE,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingAll = false, writeError = result.error)
                }
            }
        }
    }

    fun requestDelete(notification: NotificationResource) {
        _state.update { it.copy(confirmDeleteFor = notification) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.removeNotification(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            NotificationsNotice.DELETED_QUEUED
                        } else {
                            NotificationsNotice.DELETED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
