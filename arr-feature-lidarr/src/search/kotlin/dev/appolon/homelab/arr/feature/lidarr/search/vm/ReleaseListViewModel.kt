package dev.appolon.homelab.arr.feature.lidarr.search.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseGroupFilter
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseRowUi
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseScope
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.feature.lidarr.search.data.LidarrReleaseRepository
import dev.appolon.homelab.arr.feature.lidarr.search.data.filterReleaseRows
import dev.appolon.homelab.arr.feature.lidarr.search.data.releaseRow
import dev.appolon.homelab.arr.feature.lidarr.search.data.sortReleaseRows
import dev.appolon.homelab.arr.ui.lce.LceState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot grab and push outcomes, mapped to strings by the screen. */
sealed interface ReleaseMessage {
    data object Grabbed : ReleaseMessage
    data object GrabQueued : ReleaseMessage
    data class GrabFailed(val error: ArrError) : ReleaseMessage
    data object Pushed : ReleaseMessage
    data object PushQueued : ReleaseMessage
    data class PushFailed(val error: ArrError) : ReleaseMessage
}

/**
 * Options the grab sheet offers for one release. Lidarr's release wire model
 * carries no languages, so quality is the only override the grab accepts.
 */
data class GrabOptions(
    val qualities: List<Quality> = emptyList(),
)

/** The release the grab sheet is currently attached to, if any. */
data class GrabTarget(
    val row: ReleaseRowUi,
    val options: GrabOptions,
)

/** Fields of the external-release push dialog; title and URL are required. */
data class ExternalPushInput(
    val title: String,
    val downloadUrl: String,
    val indexer: String = "",
    val protocol: DownloadProtocol = DownloadProtocol.TORRENT,
)

/** UI state of the release list: LCE rows plus filter, sort, expansion, and grab flow. */
data class ReleaseListUiState(
    val releases: LceState<List<ReleaseRowUi>> = LceState.Loading,
    val groupFilter: ReleaseGroupFilter = ReleaseGroupFilter.ALL,
    val sort: ReleaseSort = ReleaseSort.QUALITY_SCORE,
    val expandedGuids: Set<String> = emptySet(),
    val grabTarget: GrabTarget? = null,
    val pushDialogOpen: Boolean = false,
    val pushing: Boolean = false,
    val grabbing: Boolean = false,
    val message: ReleaseMessage? = null,
    /** True when cached rows are showing because the latest refresh failed. */
    val stale: Boolean = false,
)

/**
 * Interactive search for one artist or album: cached release list with TTL
 * refresh, the album packs versus discography toggle, client-side sorting,
 * expandable rejection reasons, the grab flow with its quality override, and
 * the external-release push. The screen hides the group toggle for album
 * scope, where Lidarr only returns that album's packs.
 */
class ReleaseListViewModel(
    private val scope: ReleaseScope,
    private val repository: LidarrReleaseRepository,
    private val grabOptionsSource: suspend () -> GrabOptions,
) : ViewModel() {

    private val _state = MutableStateFlow(ReleaseListUiState())
    val state: StateFlow<ReleaseListUiState> = _state.asStateFlow()

    /** Artist ids with a queued grab or push, for the queued indicator. */
    val queuedArtistIds: StateFlow<Set<Int>> = repository.queuedArtistIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Album ids with a queued grab or push, for the queued indicator. */
    val queuedAlbumIds: StateFlow<Set<Int>> = repository.queuedAlbumIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Unfiltered rows of the last successful load; the filter re-derives from these. */
    private var loadedRows: List<ReleaseRowUi> = emptyList()

    init {
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            markRefreshing()
            when (val result = repository.refresh(scope)) {
                is ArrResult.Ok -> publishRows(result.value, stale = false)
                is ArrResult.Err -> publishLoadError(result.error)
            }
        }
    }

    fun retry() {
        load()
    }

    fun setGroupFilter(filter: ReleaseGroupFilter) {
        _state.update { it.copy(groupFilter = filter) }
        republish()
    }

    fun setSort(sort: ReleaseSort) {
        _state.update { it.copy(sort = sort) }
        republish()
    }

    fun toggleRejections(guid: String) {
        _state.update { state ->
            state.copy(
                expandedGuids = if (guid in state.expandedGuids) {
                    state.expandedGuids - guid
                } else {
                    state.expandedGuids + guid
                },
            )
        }
    }

    fun openGrabSheet(row: ReleaseRowUi) {
        viewModelScope.launch {
            val options = grabOptionsSource()
            _state.update { it.copy(grabTarget = GrabTarget(row, options)) }
        }
    }

    fun dismissGrabSheet() {
        _state.update { it.copy(grabTarget = null) }
    }

    /**
     * Grabs the sheet's release with the chosen quality override. Offline
     * grabs queue; the message tells the user which happened.
     */
    fun grab(quality: Quality?) {
        val target = _state.value.grabTarget ?: return
        if (_state.value.grabbing) return
        _state.update { it.copy(grabbing = true) }
        viewModelScope.launch {
            val outcome = repository.grabRelease(
                scope = scope,
                release = target.row.release,
                qualityOverride = quality,
            )
            _state.update { state ->
                state.copy(
                    grabbing = false,
                    grabTarget = null,
                    message = when (outcome) {
                        is ArrResult.Ok -> when (outcome.value) {
                            WriteOutcome.Synced -> ReleaseMessage.Grabbed
                            is WriteOutcome.Queued -> ReleaseMessage.GrabQueued
                        }

                        is ArrResult.Err -> ReleaseMessage.GrabFailed(outcome.error)
                    },
                )
            }
        }
    }

    fun openPushDialog() {
        _state.update { it.copy(pushDialogOpen = true) }
    }

    fun dismissPushDialog() {
        _state.update { it.copy(pushDialogOpen = false) }
    }

    /**
     * Pushes a release Lidarr did not find itself (an external indexer or a
     * pasted link) into the download chain. Offline pushes queue like grabs.
     */
    fun pushExternalRelease(input: ExternalPushInput) {
        if (_state.value.pushing) return
        val isMagnet = input.downloadUrl.startsWith("magnet:", ignoreCase = true)
        val scopeIds = when (scope) {
            is ReleaseScope.Artist -> scope.artistId
            is ReleaseScope.Album -> scope.albumId
        }
        val release = when (scope) {
            is ReleaseScope.Artist -> ReleaseResource(
                guid = "push-" + UUID.randomUUID(),
                title = input.title.trim(),
                indexer = input.indexer.trim().ifEmpty { null },
                downloadUrl = if (isMagnet) null else input.downloadUrl.trim(),
                magnetUrl = if (isMagnet) input.downloadUrl.trim() else null,
                protocol = input.protocol,
                artistId = scopeIds,
            )

            is ReleaseScope.Album -> ReleaseResource(
                guid = "push-" + UUID.randomUUID(),
                title = input.title.trim(),
                indexer = input.indexer.trim().ifEmpty { null },
                downloadUrl = if (isMagnet) null else input.downloadUrl.trim(),
                magnetUrl = if (isMagnet) input.downloadUrl.trim() else null,
                protocol = input.protocol,
                albumId = scopeIds,
            )
        }
        _state.update { it.copy(pushing = true) }
        viewModelScope.launch {
            val outcome = repository.pushRelease(release)
            _state.update { state ->
                state.copy(
                    pushing = false,
                    pushDialogOpen = false,
                    message = when (outcome) {
                        is ArrResult.Ok -> when (outcome.value) {
                            WriteOutcome.Synced -> ReleaseMessage.Pushed
                            is WriteOutcome.Queued -> ReleaseMessage.PushQueued
                        }

                        is ArrResult.Err -> ReleaseMessage.PushFailed(outcome.error)
                    },
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun load() {
        viewModelScope.launch {
            val cached = repository.cachedReleases(scope)
            if (cached.isNotEmpty()) markRefreshing()
            when (val result = repository.releases(scope)) {
                is ArrResult.Ok -> publishRows(result.value, stale = false)

                is ArrResult.Err -> {
                    if (cached.isNotEmpty()) {
                        publishRows(cached, stale = true)
                    } else {
                        publishLoadError(result.error)
                    }
                }
            }
        }
    }

    private fun markRefreshing() {
        _state.update { state ->
            val content = state.releases as? LceState.Content ?: return@update state
            state.copy(releases = content.copy(refreshing = true))
        }
    }

    private fun publishRows(rows: List<ReleaseResource>, stale: Boolean) {
        loadedRows = rows.map(::releaseRow)
        _state.update { state ->
            val filtered = sortReleaseRows(
                filterReleaseRows(loadedRows, state.groupFilter),
                state.sort,
            )
            val releases = when {
                filtered.isEmpty() && stale && state.releases is LceState.Content ->
                    state.releases

                loadedRows.isEmpty() -> LceState.Empty

                filtered.isEmpty() -> LceState.Empty

                else -> LceState.Content(filtered)
            }
            state.copy(releases = releases, stale = stale)
        }
    }

    private fun publishLoadError(error: ArrError) {
        loadedRows = emptyList()
        _state.update { it.copy(releases = LceState.Error(error), stale = false) }
    }

    private fun republish() {
        _state.update { state ->
            val content = state.releases as? LceState.Content
            if (content == null) return@update state
            val filtered = sortReleaseRows(
                filterReleaseRows(loadedRows, state.groupFilter),
                state.sort,
            )
            state.copy(
                releases = if (filtered.isEmpty()) {
                    LceState.Empty
                } else {
                    LceState.Content(filtered, refreshing = content.refreshing)
                },
            )
        }
    }
}
