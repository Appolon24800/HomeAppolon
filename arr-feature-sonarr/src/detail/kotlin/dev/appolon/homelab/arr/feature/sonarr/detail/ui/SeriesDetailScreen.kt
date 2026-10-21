package dev.appolon.homelab.arr.feature.sonarr.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.DetailMessage
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.DetailTab
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.EpisodeFileForm
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.SeasonPassForm
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.SeriesDetailUiState
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.SeriesEditForm
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesPosterUrls
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.Instant
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The series detail screen body: banner hero, seasons grid with per-season
 * monitoring and statistics, per-season episode list with file status and
 * per-episode search, history, and every sheet and dialog the actions open.
 * Two-pane on tablet widths (season list beside episode list), stacked with
 * a back chip on phones. Stateless; mutations travel the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailContent(
    state: SeriesDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetTab: (DetailTab) -> Unit,
    onSelectSeason: (Int?) -> Unit,
    onToggleSeriesMonitored: () -> Unit,
    onToggleSeasonMonitored: (Int) -> Unit,
    onToggleEpisodeMonitored: (EpisodeResource) -> Unit,
    onSearchSeries: () -> Unit,
    onSearchSeason: (Int) -> Unit,
    onSearchEpisode: (EpisodeResource) -> Unit,
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onOpenRenamePreview: () -> Unit,
    onCloseRenamePreview: () -> Unit,
    onRunRename: () -> Unit,
    onOpenImport: (Int?) -> Unit,
    onCloseImport: () -> Unit,
    onToggleImportSelected: (Int) -> Unit,
    onImportSelected: () -> Unit,
    onOpenManualSearchEpisode: (EpisodeResource) -> Unit,
    onOpenManualSearchSeason: (Int) -> Unit,
    onCloseManualSearch: () -> Unit,
    onRefreshReleases: () -> Unit,
    onGrabRelease: (dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource) -> Unit,
    onOpenFileEditor: (EpisodeResource) -> Unit,
    onCloseFileEditor: () -> Unit,
    onUpdateFileEditor: (EpisodeFileForm) -> Unit,
    onSubmitFileEditor: () -> Unit,
    onDeleteFileFromEditor: () -> Unit,
    onOpenEdit: () -> Unit,
    onCloseEdit: () -> Unit,
    onUpdateEdit: (SeriesEditForm) -> Unit,
    onSubmitEdit: () -> Unit,
    onOpenSeasonPass: () -> Unit,
    onCloseSeasonPass: () -> Unit,
    onUpdateSeasonPass: (SeasonPassForm) -> Unit,
    onSubmitSeasonPass: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (Boolean, Boolean) -> Unit,
    onRefreshHistory: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = detailMessageText(state.message)
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    var deleteFiles by remember { mutableStateOf(false) }
    var addExclusion by remember { mutableStateOf(false) }
    var confirmFileDelete by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.series?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(ArrUiR.string.arr_common_detail_cd_back),
                        )
                    }
                },
                actions = {
                    SeriesDetailMenu(
                        onRefreshMetadata = onRefreshMetadata,
                        onScanDisk = onScanDisk,
                        onSearch = onSearchSeries,
                        onRename = onOpenRenamePreview,
                        onImport = { onOpenImport(null) },
                        onEdit = onOpenEdit,
                        onDelete = onRequestDelete,
                    )
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        ArrLceBox(
            state = state.lceFor(),
            onRetry = onRefresh,
            modifier = Modifier.padding(padding),
            emptyTitle = stringResource(R.string.arr_sonarr_detail_empty_title),
            emptyMessage = stringResource(R.string.arr_sonarr_detail_empty_message),
        ) { content ->
            ArrPullToRefreshBox(
                isRefreshing = content.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    SeriesHero(
                        series = content.series,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        queued = content.queuedForSeries,
                        onToggleMonitored = onToggleSeriesMonitored,
                    )
                    if (content.queuedForServer > 0) {
                        QueuedBanner(count = content.queuedForServer)
                    }
                    TabRow(selectedTabIndex = content.tab.ordinal) {
                        Tab(
                            selected = content.tab == DetailTab.SEASONS,
                            onClick = { onSetTab(DetailTab.SEASONS) },
                            text = { Text(stringResource(R.string.arr_sonarr_detail_tab_seasons)) },
                        )
                        Tab(
                            selected = content.tab == DetailTab.HISTORY,
                            onClick = { onSetTab(DetailTab.HISTORY) },
                            text = { Text(stringResource(ArrUiR.string.arr_common_history_title)) },
                        )
                    }
                    if (content.tab == DetailTab.SEASONS) {
                        SeasonsPane(
                            state = content,
                            onSelectSeason = onSelectSeason,
                            onToggleSeasonMonitored = onToggleSeasonMonitored,
                            onSearchSeason = onSearchSeason,
                            onOpenManualSearchSeason = onOpenManualSearchSeason,
                            onSearchEpisode = onSearchEpisode,
                            onToggleEpisodeMonitored = onToggleEpisodeMonitored,
                            onOpenManualSearchEpisode = onOpenManualSearchEpisode,
                            onOpenFileEditor = onOpenFileEditor,
                        )
                    } else {
                        HistoryPane(state = content, onRefresh = onRefreshHistory)
                    }
                }
            }
        }
    }

    state.editForm?.let { form ->
        EditSeriesSheet(
            form = form,
            qualityProfiles = state.qualityProfiles,
            tagLabels = { id -> state.tagLabel(id) },
            onDismiss = onCloseEdit,
            onUpdate = onUpdateEdit,
            onSubmit = onSubmitEdit,
            onOpenSeasonPass = onOpenSeasonPass,
        )
    }
    state.seasonPassForm?.let { form ->
        SeasonPassStrategySheet(
            form = form,
            onDismiss = onCloseSeasonPass,
            onUpdate = onUpdateSeasonPass,
            onSubmit = onSubmitSeasonPass,
        )
    }
    state.fileForm?.let { form ->
        EpisodeFileEditorSheet(
            form = form,
            qualities = state.qualities,
            languages = state.languages,
            qualityLabel = { id -> state.qualityLabel(id) },
            languageLabel = { id -> state.languageLabel(id) },
            confirmDelete = confirmFileDelete,
            onConfirmDeleteChange = { confirmFileDelete = it },
            onDismiss = onCloseFileEditor,
            onUpdate = onUpdateFileEditor,
            onSubmit = onSubmitFileEditor,
            onDelete = onDeleteFileFromEditor,
        )
    }
    state.renamePreview?.let { preview ->
        RenamePreviewDialog(
            preview = preview,
            onDismiss = onCloseRenamePreview,
            onConfirm = onRunRename,
        )
    }
    state.importState?.let { importState ->
        InteractiveImportSheet(
            state = importState,
            onDismiss = onCloseImport,
            onToggleSelected = onToggleImportSelected,
            onImport = onImportSelected,
        )
    }
    state.searchTarget?.let { target ->
        ManualSearchSheet(
            target = target,
            releases = state.releases,
            loading = state.releasesLoading,
            error = state.releasesError,
            onDismiss = onCloseManualSearch,
            onRefresh = onRefreshReleases,
            onGrab = onGrabRelease,
        )
    }
    if (state.confirmDelete) {
        DeleteSeriesDialog(
            title = state.series?.title.orEmpty(),
            deleteFiles = deleteFiles,
            addExclusion = addExclusion,
            onDeleteFilesChange = { deleteFiles = it },
            onAddExclusionChange = { addExclusion = it },
            onDismiss = onDismissDelete,
            onConfirm = { onConfirmDelete(deleteFiles, addExclusion) },
        )
    }
}

@Composable
private fun SeriesDetailMenu(
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onSearch: () -> Unit,
    onRename: () -> Unit,
    onImport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.arr_sonarr_detail_cd_more),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(ArrUiR.string.arr_common_library_cd_refresh_metadata)) },
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            onClick = {
                open = false
                onRefreshMetadata()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_sonarr_detail_menu_scan)) },
            leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
            onClick = {
                open = false
                onScanDisk()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_sonarr_detail_menu_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = {
                open = false
                onSearch()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_sonarr_detail_menu_rename)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                open = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(ArrUiR.string.arr_common_import_flow_title)) },
            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
            onClick = {
                open = false
                onImport()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_sonarr_detail_menu_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                open = false
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_sonarr_detail_menu_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                open = false
                onDelete()
            },
        )
    }
}

/** Banner hero with title, meta line, and the series monitoring toggle. */
@Composable
private fun SeriesHero(
    series: SeriesResource?,
    baseUrl: String,
    imageLoader: ImageLoader?,
    queued: Boolean,
    onToggleMonitored: () -> Unit,
) {
    val current = series ?: return
    Box(Modifier.fillMaxWidth().height(196.dp)) {
        val banner = SeriesPosterUrls.bannerUrlOf(current, baseUrl)
        val bannerCd = stringResource(R.string.arr_sonarr_detail_cd_series_banner, current.title.orEmpty())
        if (banner != null && imageLoader != null) {
            AsyncImage(
                model = banner,
                imageLoader = imageLoader,
                contentDescription = bannerCd,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                ),
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = current.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = stringResource(
                    R.string.arr_sonarr_detail_meta,
                    current.year,
                    current.runtime,
                    current.network.orEmpty(),
                )
                Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                if (queued) {
                    Text(
                        text = stringResource(R.string.arr_sonarr_cd_queued_change),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            val monitoredCd = if (current.monitored) {
                stringResource(
                    R.string.arr_sonarr_detail_cd_monitored,
                    current.title.orEmpty(),
                )
            } else {
                stringResource(
                    R.string.arr_sonarr_detail_cd_not_monitored,
                    current.title.orEmpty(),
                )
            }
            IconButton(onClick = onToggleMonitored, modifier = Modifier.semantics {
                contentDescription = monitoredCd
            }) {
                Icon(
                    imageVector = if (current.monitored) {
                        Icons.Filled.Visibility
                    } else {
                        Icons.Filled.VisibilityOff
                    },
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun QueuedBanner(count: Int) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(ArrUiR.string.arr_common_system_pending_banner, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * Seasons and episodes with a width-adaptive layout: master-detail beside
 * 720dp, stacked navigation below it.
 */
@Composable
private fun SeasonsPane(
    state: SeriesDetailUiState,
    onSelectSeason: (Int?) -> Unit,
    onToggleSeasonMonitored: (Int) -> Unit,
    onSearchSeason: (Int) -> Unit,
    onOpenManualSearchSeason: (Int) -> Unit,
    onSearchEpisode: (EpisodeResource) -> Unit,
    onToggleEpisodeMonitored: (EpisodeResource) -> Unit,
    onOpenManualSearchEpisode: (EpisodeResource) -> Unit,
    onOpenFileEditor: (EpisodeResource) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 720.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                SeasonListColumn(
                    state = state,
                    onSelectSeason = onSelectSeason,
                    onToggleSeasonMonitored = onToggleSeasonMonitored,
                    onSearchSeason = onSearchSeason,
                    onOpenManualSearchSeason = onOpenManualSearchSeason,
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxSize(),
                )
                EpisodeListPane(
                    state = state,
                    onBack = null,
                    onSearchEpisode = onSearchEpisode,
                    onToggleEpisodeMonitored = onToggleEpisodeMonitored,
                    onOpenManualSearchEpisode = onOpenManualSearchEpisode,
                    onOpenFileEditor = onOpenFileEditor,
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxSize(),
                )
            }
        } else if (state.selectedSeason == null) {
            SeasonGrid(
                state = state,
                onSelectSeason = onSelectSeason,
                onToggleSeasonMonitored = onToggleSeasonMonitored,
                onSearchSeason = onSearchSeason,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                TextButton(
                    onClick = { onSelectSeason(null) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.arr_sonarr_detail_back_to_seasons))
                }
                EpisodeListPane(
                    state = state,
                    onBack = { onSelectSeason(null) },
                    onSearchEpisode = onSearchEpisode,
                    onToggleEpisodeMonitored = onToggleEpisodeMonitored,
                    onOpenManualSearchEpisode = onOpenManualSearchEpisode,
                    onOpenFileEditor = onOpenFileEditor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Phone layout: season cards in a grid. */
@Composable
private fun SeasonGrid(
    state: SeriesDetailUiState,
    onSelectSeason: (Int?) -> Unit,
    onToggleSeasonMonitored: (Int) -> Unit,
    onSearchSeason: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.seasons, key = { it.seasonNumber }, contentType = { "season" }) { season ->
            SeasonCard(
                season = season,
                selected = false,
                onClick = { onSelectSeason(season.seasonNumber) },
                onToggleMonitored = { onToggleSeasonMonitored(season.seasonNumber) },
                onSearch = null,
            )
        }
    }
}

/** Tablet master column: compact season rows. */
@Composable
private fun SeasonListColumn(
    state: SeriesDetailUiState,
    onSelectSeason: (Int?) -> Unit,
    onToggleSeasonMonitored: (Int) -> Unit,
    onSearchSeason: (Int) -> Unit,
    onOpenManualSearchSeason: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(state.seasons, key = { it.seasonNumber }, contentType = { "season" }) { season ->
            val selected = state.selectedSeason == season.seasonNumber
            SeasonCard(
                season = season,
                selected = selected,
                onClick = { onSelectSeason(season.seasonNumber) },
                onToggleMonitored = { onToggleSeasonMonitored(season.seasonNumber) },
                onSearch = { onSearchSeason(season.seasonNumber) },
            )
        }
    }
}

/** One season card: monitoring toggle, counts, size, download progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonCard(
    season: SeasonResource,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleMonitored: () -> Unit,
    onSearch: (() -> Unit)?,
) {
    val label = seasonLabel(season.seasonNumber)
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onSearch != null) {
                    IconButton(onClick = onSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(
                                R.string.arr_sonarr_detail_cd_search_season,
                                season.seasonNumber,
                            ),
                        )
                    }
                }
                val monitoredCd = if (season.monitored) {
                    stringResource(R.string.arr_sonarr_detail_cd_monitored, label)
                } else {
                    stringResource(R.string.arr_sonarr_detail_cd_not_monitored, label)
                }
                IconButton(onClick = onToggleMonitored, modifier = Modifier.semantics {
                    contentDescription = monitoredCd
                }) {
                    Icon(
                        imageVector = if (season.monitored) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        },
                        contentDescription = null,
                        tint = if (season.monitored) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            val stats = season.statistics
            Text(
                text = stringResource(
                    R.string.arr_sonarr_detail_season_episodes,
                    stats.episodeFileCount,
                    stats.episodeCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.sizeOnDisk > 0) {
                Text(
                    text = Humanize.bytes(stats.sizeOnDisk),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val progressCd = stringResource(
                R.string.arr_sonarr_detail_cd_season_progress,
                label,
                stats.percentOfEpisodes.toInt(),
            )
            LinearProgressIndicator(
                progress = { (stats.percentOfEpisodes / 100.0).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .semantics { contentDescription = progressCd },
            )
        }
    }
}

@Composable
private fun seasonLabel(seasonNumber: Int): String =
    if (seasonNumber == 0) {
        stringResource(R.string.arr_sonarr_add_season_specials)
    } else {
        stringResource(R.string.arr_sonarr_wanted_filter_season_option, seasonNumber)
    }

/** Episode list pane, shared by the phone stack and the tablet detail side. */
@Composable
private fun EpisodeListPane(
    state: SeriesDetailUiState,
    onBack: (() -> Unit)?,
    onSearchEpisode: (EpisodeResource) -> Unit,
    onToggleEpisodeMonitored: (EpisodeResource) -> Unit,
    onOpenManualSearchEpisode: (EpisodeResource) -> Unit,
    onOpenFileEditor: (EpisodeResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes = state.seasonEpisodes
    if (episodes.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.arr_sonarr_detail_episodes_empty),
            message = "",
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(episodes, key = { it.id }, contentType = { "episode" }) { episode ->
            EpisodeRow(
                episode = episode,
                state = state,
                onSearch = onSearchEpisode,
                onToggleMonitored = onToggleEpisodeMonitored,
                onOpenManualSearch = onOpenManualSearchEpisode,
                onOpenFileEditor = onOpenFileEditor,
            )
        }
    }
}

/** One episode row plus its expandable details and history. */
@Composable
private fun EpisodeRow(
    episode: EpisodeResource,
    state: SeriesDetailUiState,
    onSearch: (EpisodeResource) -> Unit,
    onToggleMonitored: (EpisodeResource) -> Unit,
    onOpenManualSearch: (EpisodeResource) -> Unit,
    onOpenFileEditor: (EpisodeResource) -> Unit,
) {
    var expanded by remember(episode.id) { mutableStateOf(false) }
    val title = episode.title.orEmpty()
    val label = stringResource(
        R.string.arr_sonarr_detail_episode_label,
        episode.seasonNumber,
        episode.episodeNumber,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 52.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            R.string.arr_sonarr_detail_cd_expand_episode,
                            title,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = episodeStatusLine(episode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onSearch(episode) }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(
                        ArrUiR.string.arr_common_wanted_cd_search_movie,
                        title,
                    ),
                )
            }
            val monitoredCd = if (episode.monitored) {
                stringResource(R.string.arr_sonarr_detail_cd_monitored, label)
            } else {
                stringResource(R.string.arr_sonarr_detail_cd_not_monitored, label)
            }
            IconButton(onClick = { onToggleMonitored(episode) }, modifier = Modifier.semantics {
                contentDescription = monitoredCd
            }) {
                Icon(
                    imageVector = if (episode.monitored) {
                        Icons.Filled.Visibility
                    } else {
                        Icons.Filled.VisibilityOff
                    },
                    contentDescription = null,
                    tint = if (episode.monitored) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenManualSearch(episode) }) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = stringResource(
                                R.string.arr_sonarr_detail_cd_manual_search_episode,
                                label,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.arr_sonarr_detail_manual_search_action))
                    }
                    if (episode.hasFile) {
                        TextButton(onClick = { onOpenFileEditor(episode) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(
                                    R.string.arr_sonarr_detail_cd_edit_file,
                                    label,
                                ),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.arr_sonarr_detail_file_title))
                        }
                    }
                }
                val history = state.historyFor(episode.id)
                if (history.isNotEmpty()) {
                    Text(
                        text = stringResource(ArrUiR.string.arr_common_history_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    history.take(5).forEach { event -> HistoryRow(event = event) }
                }
            }
        }
    }
}

/** "HDTV 1080p · 1.4 GB", "Missing", or the air date for unaired episodes. */
@Composable
private fun episodeStatusLine(episode: EpisodeResource): String {
    if (episode.hasFile) {
        val file = episode.episodeFile
        val quality = file.quality.quality.name
            ?.let(Humanize::qualityName)
            ?: stringResource(R.string.arr_sonarr_detail_file_title)
        return stringResource(ArrUiR.string.arr_common_metadata_pair, quality, Humanize.bytes(file.size))
    }
    val airDate = ArrTime.parse(episode.airDateUtc ?: episode.airDate)
    return when {
        airDate == null -> stringResource(ArrUiR.string.arr_common_wanted_tab_missing)
        airDate.isAfter(Instant.now()) -> stringResource(
            R.string.arr_sonarr_detail_airing_on,
            ArrTime.dateTimeShort(airDate),
        )

        else -> stringResource(ArrUiR.string.arr_common_wanted_tab_missing)
    }
}

/** History tab: refresh button plus every event for the series. */
@Composable
private fun HistoryPane(state: SeriesDetailUiState, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_detail_cd_refresh_history),
                )
            }
        }
        if (state.history.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.arr_sonarr_detail_history_empty),
                message = "",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    state.history,
                    key = { it.id },
                    contentType = { "event" },
                ) { event -> HistoryRow(event = event) }
            }
        }
    }
}

/** One history event: type badge, source title, quality, age. */
@Composable
private fun HistoryRow(event: HistoryResource) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = historyEventLabel(event.eventType),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            event.sourceTitle?.takeIf { it.isNotBlank() }?.let { source ->
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val quality = event.quality.quality.name
            if (quality != null) {
                Text(
                    text = Humanize.qualityName(quality),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ArrTime.parse(event.date)?.let { date ->
            Text(
                text = ArrTime.relative(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun historyEventLabel(type: EpisodeHistoryEventType): String = when (type) {
    EpisodeHistoryEventType.GRABBED -> stringResource(ArrUiR.string.arr_common_history_filter_grabbed)
    EpisodeHistoryEventType.SERIES_FOLDER_IMPORTED,
    EpisodeHistoryEventType.DOWNLOAD_FOLDER_IMPORTED,
    -> stringResource(ArrUiR.string.arr_common_history_filter_imported)

    EpisodeHistoryEventType.DOWNLOAD_FAILED -> stringResource(ArrUiR.string.arr_common_detail_event_failed)
    EpisodeHistoryEventType.EPISODE_FILE_DELETED -> stringResource(ArrUiR.string.arr_common_detail_event_file_deleted)
    EpisodeHistoryEventType.EPISODE_FILE_RENAMED -> stringResource(R.string.arr_sonarr_detail_history_renamed)
    EpisodeHistoryEventType.DOWNLOAD_IGNORED -> stringResource(ArrUiR.string.arr_common_history_type_ignored)
    EpisodeHistoryEventType.UNKNOWN -> stringResource(ArrUiR.string.arr_common_detail_event_unknown)
}

@Composable
internal fun importActionLabel(count: Int): String =
    pluralStringResource(R.plurals.arr_sonarr_detail_import_action, count, count)

/** Snackbar text for one write result; null clears the queue. */
@Composable
internal fun detailMessageText(message: DetailMessage?): String? = when (message) {
    null -> null
    DetailMessage.WriteSynced -> stringResource(R.string.arr_sonarr_detail_message_synced)
    DetailMessage.WriteQueued -> stringResource(R.string.arr_sonarr_detail_message_queued)
    DetailMessage.SearchStarted -> stringResource(ArrUiR.string.arr_common_detail_msg_search_started)
    DetailMessage.SearchQueued -> stringResource(R.string.arr_sonarr_detail_message_search_queued)
    DetailMessage.GrabSent -> stringResource(R.string.arr_sonarr_detail_message_grabbed)
    DetailMessage.GrabQueued -> stringResource(R.string.arr_sonarr_detail_message_grab_queued)
    is DetailMessage.ImportSynced -> pluralStringResource(
        R.plurals.arr_sonarr_detail_message_imported,
        message.count,
        message.count,
    )

    DetailMessage.ImportQueued -> stringResource(R.string.arr_sonarr_detail_message_import_queued)
    DetailMessage.RenameStarted -> stringResource(R.string.arr_sonarr_detail_message_rename_started)
    DetailMessage.RenameQueued -> stringResource(R.string.arr_sonarr_detail_message_rename_queued)
    DetailMessage.DeleteSynced -> stringResource(R.string.arr_sonarr_detail_message_delete_synced)
    DetailMessage.DeleteQueued -> stringResource(R.string.arr_sonarr_detail_message_delete_queued)
    is DetailMessage.WriteFailed -> stringResource(ArrUiR.string.arr_common_msg_write_failed)
}

/** LCE projection of the detail state. */
internal fun SeriesDetailUiState.lceFor(): LceState<SeriesDetailUiState> = when {
    deleted -> LceState.Empty
    loadError != null && series == null -> LceState.Error(loadError)
    !loaded -> LceState.Loading
    series == null -> LceState.Empty
    else -> LceState.Content(this, refreshing = refreshing)
}
