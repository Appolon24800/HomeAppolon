package dev.appolon.homelab.arr.feature.radarr.activity.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.activity.RadarrActivityClients
import dev.appolon.homelab.arr.feature.radarr.activity.RadarrActivityFeature
import dev.appolon.homelab.arr.feature.radarr.activity.data.RadarrHistoryRepository
import dev.appolon.homelab.arr.feature.radarr.activity.logic.HistoryDateWindow
import dev.appolon.homelab.arr.feature.radarr.activity.logic.HistoryEventTypeFilter
import dev.appolon.homelab.arr.feature.radarr.activity.logic.RadarrHistoryEventUi
import dev.appolon.homelab.arr.feature.radarr.activity.vm.HistoryNotice
import dev.appolon.homelab.arr.feature.radarr.activity.vm.RadarrHistoryContent
import dev.appolon.homelab.arr.feature.radarr.activity.vm.RadarrHistoryViewModel
import dev.appolon.homelab.arr.feature.radarr.queue.RadarrQueueFeature
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Destination for the history route: wires the repository and ViewModel for
 * this route's server. Failed-import retry links navigate into the manual
 * import route the queue group registered.
 */
@Composable
fun RadarrHistoryDestination(
    serverId: ServerId,
    clients: RadarrActivityClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        RadarrHistoryRepository(
            serverId = serverId,
            store = clients.store,
            historyApi = clients.historyApi,
            actionQueue = clients.actionQueue,
        )
    }
    val vm: RadarrHistoryViewModel = viewModel(key = "radarr-history-" + serverId.value) {
        RadarrHistoryViewModel(
            serverId = serverId,
            repository = repository,
            refreshSignals = refreshSignals,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current

    RadarrHistoryScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onRetryLoadMore = vm::loadMore,
        onTypeFilter = vm::setTypeFilter,
        onDateWindow = vm::setDateWindow,
        onMovieQuery = vm::setMovieQuery,
        onMarkFailed = vm::markFailed,
        onOpenBlocklist = { RadarrActivityFeature.openBlocklist(navigator, serverId) },
        onOpenManualImport = { downloadId ->
            RadarrQueueFeature.openManualImport(navigator, serverId, downloadId)
        },
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The history feed: event-type and date-window chips, a movie search field,
 * paged infinite scroll, and a per-event detail sheet (a side pane on tablet
 * widths, a bottom sheet on phones). Every icon button carries a content
 * description and Material's 48dp minimum touch target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrHistoryScreen(
    state: RadarrHistoryViewModel.UiState,
    lce: LceState<RadarrHistoryContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onTypeFilter: (HistoryEventTypeFilter) -> Unit,
    onDateWindow: (HistoryDateWindow) -> Unit,
    onMovieQuery: (String) -> Unit,
    onMarkFailed: (RadarrHistoryEventUi) -> Unit,
    onOpenBlocklist: () -> Unit,
    onOpenManualImport: (downloadId: String?) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedEventId by rememberSaveable { mutableStateOf<Int?>(null) }
    val content = (lce as? LceState.Content<RadarrHistoryContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrHistoryContent>).refreshing
    // Resolved up front: the chip row's label lambdas are not composable.
    val typeLabels = typeFilterLabels()
    val windowLabels = dateWindowLabels()

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_history_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenBlocklist) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = stringResource(ArrUiR.string.arr_common_history_cd_blocklist),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_history_cd_refresh),
                )
            }
        },
        filterBar = {
            Column {
                FilterChipRow(
                    filters = HistoryEventTypeFilter.entries,
                    selected = { it == state.typeFilter },
                    label = { typeLabels.getValue(it) },
                    onSelect = onTypeFilter,
                )
                FilterChipRow(
                    filters = HistoryDateWindow.entries,
                    selected = { it == state.dateWindow },
                    label = { windowLabels.getValue(it) },
                    onSelect = onDateWindow,
                    modifier = Modifier.padding(top = 4.dp),
                )
                MovieQueryField(
                    query = state.movieQuery,
                    onQuery = onMovieQuery,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        .fillMaxWidth(),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tablet-aware: 840dp and wider renders the detail as a side pane.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val twoPane = maxWidth >= ExpandedPaneWidth
                if (twoPane) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        HistoryList(
                            state = state,
                            content = content,
                            onSelectEvent = { selectedEventId = it },
                            onLoadMore = onLoadMore,
                            onRetryLoadMore = onRetryLoadMore,
                            modifier = Modifier.weight(1f),
                        )
                        val selected = content?.events?.find { it.id == selectedEventId }
                        if (selected != null) {
                            EventDetailContent(
                                event = selected,
                                onMarkFailed = { onMarkFailed(selected) },
                                onManualImport = {
                                    onOpenManualImport(selected.downloadId)
                                    selectedEventId = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(16.dp),
                            )
                        }
                    }
                } else {
                    HistoryList(
                        state = state,
                        content = content,
                        onSelectEvent = { selectedEventId = it },
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    val selected = content?.events?.find { it.id == selectedEventId }
    if (selected != null) {
        EventDetailSheet(
            event = selected,
            onMarkFailed = {
                onMarkFailed(selected)
                selectedEventId = null
            },
            onManualImport = {
                onOpenManualImport(selected.downloadId)
                selectedEventId = null
            },
            onDismiss = { selectedEventId = null },
        )
    }
}

@Composable
private fun HistoryList(
    state: RadarrHistoryViewModel.UiState,
    content: RadarrHistoryContent?,
    onSelectEvent: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    InfiniteScrollEffect(
        listState = listState,
        enabled = content?.canLoadMore == true,
        onLoadMore = onLoadMore,
    )
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (content == null) return@LazyColumn
        if (content.pendingActions > 0) {
            item(key = PendingBannerKey) { PendingSyncBanner(content.pendingActions) }
        }
        items(content.events, key = { it.id }, contentType = { "event" }) { event ->
            HistoryEventRow(
                event = event,
                onClick = { onSelectEvent(event.id) },
            )
        }
        when {
            content.loadingMore -> item(key = LoadingMoreKey) { LoadingMoreRow() }
            state.loadMoreFailed -> item(key = LoadMoreFailedKey) {
                LoadMoreFailedRow(onRetry = onRetryLoadMore)
            }
        }
    }
}

/**
 * Fires [onLoadMore] while the last visible row is within
 * [LoadMoreTriggerMargin] rows of the end. The derived state re-evaluates as
 * the list grows, so one callback keeps appending until the pager stops it.
 */
@Composable
private fun InfiniteScrollEffect(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LoadMoreTriggerMargin
        }
    }
    LaunchedEffect(nearEnd, enabled) {
        if (nearEnd && enabled) onLoadMore()
    }
}

@Composable
private fun HistoryEventRow(
    event: RadarrHistoryEventUi,
    onClick: () -> Unit,
) {
    val clickLabel = stringResource(ArrUiR.string.arr_common_roots_cd_open, event.title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = event.eventType.icon(),
            contentDescription = stringResource(
                ArrUiR.string.arr_common_history_cd_event_type,
                eventTypeLabel(event.eventType),
            ),
            tint = when (event.eventType) {
                MovieHistoryEventType.DOWNLOAD_FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(
                        event.sourceTitle,
                        event.qualityName,
                        event.relativeDate,
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (event.queuedOffline) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_system_cd_queued_indicator,
                        ),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Per-event detail as a bottom sheet on compact widths. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    event: RadarrHistoryEventUi,
    onMarkFailed: () -> Unit,
    onManualImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        EventDetailContent(
            event = event,
            onMarkFailed = onMarkFailed,
            onManualImport = onManualImport,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        )
    }
}

/** Detail lines shared by the phone sheet and the tablet side pane. */
@Composable
private fun EventDetailContent(
    event: RadarrHistoryEventUi,
    onMarkFailed: () -> Unit,
    onManualImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.widthIn(max = 640.dp)) {
        Text(
            text = stringResource(
                ArrUiR.string.arr_common_history_cd_event_type,
                eventTypeLabel(event.eventType),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = event.title + (event.movieYear?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        event.fullDate?.let { DetailLine(label = null, value = it) }
        event.qualityName?.let {
            DetailLine(label = stringResource(ArrUiR.string.arr_common_quality), value = it)
        }
        event.source?.let {
            DetailLine(label = stringResource(ArrUiR.string.arr_common_history_details_source), value = it)
        }
        event.ageText?.let {
            DetailLine(label = stringResource(ArrUiR.string.arr_common_releases_sort_age), value = it)
        }
        event.indexer?.let {
            DetailLine(label = stringResource(ArrUiR.string.arr_common_releases_details_indexer), value = it)
        }
        event.downloadClient?.let {
            DetailLine(
                label = stringResource(ArrUiR.string.arr_common_history_details_download_client),
                value = it,
            )
        }
        event.sizeText?.let {
            DetailLine(label = stringResource(ArrUiR.string.arr_common_size), value = it)
        }
        event.releaseGroup?.let {
            DetailLine(
                label = stringResource(ArrUiR.string.arr_common_history_details_release_group),
                value = it,
            )
        }
        if (event.customFormatNames.isNotEmpty()) {
            DetailLine(
                label = stringResource(ArrUiR.string.arr_common_quality_custom_formats_title),
                value = event.customFormatNames.joinToString(", "),
            )
        }
        if (event.customFormatScore != 0) {
            DetailLine(
                label = stringResource(ArrUiR.string.arr_common_releases_sort_quality),
                value = stringResource(ArrUiR.string.arr_common_history_score_value, event.customFormatScore),
            )
        }
        event.message?.let { message ->
            Text(
                text = stringResource(ArrUiR.string.arr_common_history_details_message),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            if (event.failedImport) {
                TextButton(onClick = onManualImport) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(ArrUiR.string.arr_common_history_details_action_manual_import))
                }
            }
            if (event.canMarkFailed) {
                TextButton(onClick = onMarkFailed) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(ArrUiR.string.arr_common_history_details_action_mark_failed))
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String?, value: String) {
    val text = if (label == null) {
        value
    } else {
        stringResource(ArrUiR.string.arr_common_history_details_line, label, value)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun MovieQueryField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.arr_radarr_history_movie_query_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun PendingSyncBanner(pending: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(ArrUiR.string.arr_common_system_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun LoadingMoreRow() {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun LoadMoreFailedRow(onRetry: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        TextButton(onClick = onRetry) {
            Text(stringResource(ArrUiR.string.arr_common_history_load_more_retry))
        }
    }
}

@Composable
private fun NoticeHost(
    notice: HistoryNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        HistoryNotice.MARKED_FAILED -> stringResource(ArrUiR.string.arr_common_history_notice_marked_failed)
        HistoryNotice.MARK_FAILED_QUEUED ->
            stringResource(ArrUiR.string.arr_common_history_notice_mark_failed_queued)
        null -> null
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumeNotice()
        }
    }
    val errorText = writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }
}

/** Localized label for one wire event type. */
@Composable
internal fun eventTypeLabel(type: MovieHistoryEventType): String = stringResource(
    when (type) {
        MovieHistoryEventType.UNKNOWN -> ArrUiR.string.arr_common_status_unknown
        MovieHistoryEventType.GRABBED -> ArrUiR.string.arr_common_history_filter_grabbed
        MovieHistoryEventType.DOWNLOAD_FOLDER_IMPORTED -> ArrUiR.string.arr_common_history_filter_imported
        MovieHistoryEventType.MOVIE_FOLDER_IMPORTED -> ArrUiR.string.arr_common_history_filter_imported
        MovieHistoryEventType.DOWNLOAD_FAILED -> ArrUiR.string.arr_common_history_filter_failed
        MovieHistoryEventType.MOVIE_FILE_DELETED -> ArrUiR.string.arr_common_connect_notice_deleted
        MovieHistoryEventType.MOVIE_FILE_RENAMED -> ArrUiR.string.arr_common_history_filter_renamed
        MovieHistoryEventType.DOWNLOAD_IGNORED -> ArrUiR.string.arr_common_history_type_ignored
    },
)

private fun MovieHistoryEventType.icon(): ImageVector = when (this) {
    MovieHistoryEventType.GRABBED -> Icons.Filled.Download
    MovieHistoryEventType.DOWNLOAD_FOLDER_IMPORTED -> Icons.Filled.Input
    MovieHistoryEventType.MOVIE_FOLDER_IMPORTED -> Icons.Filled.Input
    MovieHistoryEventType.DOWNLOAD_FAILED -> Icons.Filled.Error
    MovieHistoryEventType.MOVIE_FILE_DELETED -> Icons.Filled.Delete
    MovieHistoryEventType.MOVIE_FILE_RENAMED -> Icons.Filled.DriveFileRenameOutline
    MovieHistoryEventType.DOWNLOAD_IGNORED -> Icons.Filled.Block
    MovieHistoryEventType.UNKNOWN -> Icons.Filled.Schedule
}

@Composable
private fun typeFilterLabels(): Map<HistoryEventTypeFilter, String> = mapOf(
    HistoryEventTypeFilter.ALL to stringResource(ArrUiR.string.arr_common_history_filter_all),
    HistoryEventTypeFilter.GRABBED to stringResource(ArrUiR.string.arr_common_history_filter_grabbed),
    HistoryEventTypeFilter.IMPORTED to stringResource(ArrUiR.string.arr_common_history_filter_imported),
    HistoryEventTypeFilter.FAILED to stringResource(ArrUiR.string.arr_common_history_filter_failed),
    HistoryEventTypeFilter.DELETED to stringResource(ArrUiR.string.arr_common_connect_notice_deleted),
    HistoryEventTypeFilter.RENAMED to stringResource(ArrUiR.string.arr_common_history_filter_renamed),
)

@Composable
private fun dateWindowLabels(): Map<HistoryDateWindow, String> = mapOf(
    HistoryDateWindow.ALL_TIME to stringResource(ArrUiR.string.arr_common_history_window_all),
    HistoryDateWindow.LAST_24_HOURS to stringResource(ArrUiR.string.arr_common_history_window_24h),
    HistoryDateWindow.LAST_7_DAYS to stringResource(ArrUiR.string.arr_common_history_window_7d),
    HistoryDateWindow.LAST_30_DAYS to stringResource(ArrUiR.string.arr_common_history_window_30d),
)

private const val PendingBannerKey = "__radarr_history_pending"
private const val LoadingMoreKey = "__radarr_history_loading_more"
private const val LoadMoreFailedKey = "__radarr_history_load_more_failed"

/** Rows from the end that trigger the next page fetch. */
private const val LoadMoreTriggerMargin = 6

/** Material window size class breakpoint for expanded tablet widths. */
private val ExpandedPaneWidth = 840.dp
