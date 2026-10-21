package dev.appolon.homelab.arr.feature.lidarr.detail.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.detail.data.ArtistDetailCoverUrls
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.AlbumGroup
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.AlbumReleaseType
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.ArtistDetailUiState
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.ArtistEditForm
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.DetailMessage
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.DetailTab
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.TrackFileForm
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState

/**
 * The artist detail screen body: hero with cover art and metadata, albums
 * grouped by release type with per-album monitoring, statistics, quality and
 * actions, per-album track lists with file status, history, and every sheet
 * and dialog the actions open. Two-pane on tablet widths (album list beside
 * track list), expandable album cards on phones. Stateless; mutations travel
 * the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailContent(
    state: ArtistDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetTab: (DetailTab) -> Unit,
    onSelectAlbum: (Int?) -> Unit,
    onToggleArtistMonitored: () -> Unit,
    onToggleAlbumMonitored: (AlbumResource) -> Unit,
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onSearchArtist: () -> Unit,
    onSearchAlbum: (Int) -> Unit,
    onOpenRenamePreview: (Int?) -> Unit,
    onCloseRenamePreview: () -> Unit,
    onRunRename: () -> Unit,
    onOpenRetagPreview: (Int?) -> Unit,
    onCloseRetagPreview: () -> Unit,
    onRunRetag: () -> Unit,
    onOpenImport: () -> Unit,
    onCloseImport: () -> Unit,
    onToggleImportSelected: (Int) -> Unit,
    onImportSelected: () -> Unit,
    onOpenManualSearchArtist: () -> Unit,
    onOpenManualSearchAlbum: (AlbumResource) -> Unit,
    onCloseManualSearch: () -> Unit,
    onRefreshReleases: () -> Unit,
    onGrabRelease: (dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource) -> Unit,
    onOpenFileEditor: (TrackResource) -> Unit,
    onCloseFileEditor: () -> Unit,
    onUpdateFileEditor: (TrackFileForm) -> Unit,
    onSubmitFileEditor: () -> Unit,
    onDeleteFileFromEditor: () -> Unit,
    onOpenEdit: () -> Unit,
    onCloseEdit: () -> Unit,
    onUpdateEdit: (ArtistEditForm) -> Unit,
    onSubmitEdit: () -> Unit,
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
                        text = state.artist?.artistName.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.arr_lidarr_detail_cd_back),
                        )
                    }
                },
                actions = {
                    ArtistDetailMenu(
                        onRefreshMetadata = onRefreshMetadata,
                        onScanDisk = onScanDisk,
                        onSearch = onSearchArtist,
                        onRename = { onOpenRenamePreview(null) },
                        onRetag = { onOpenRetagPreview(null) },
                        onImport = onOpenImport,
                        onManualSearch = onOpenManualSearchArtist,
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
            emptyTitle = stringResource(R.string.arr_lidarr_detail_empty_title),
            emptyMessage = stringResource(R.string.arr_lidarr_detail_empty_message),
        ) { content ->
            ArrPullToRefreshBox(
                isRefreshing = content.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    ArtistHero(
                        state = content,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onToggleMonitored = onToggleArtistMonitored,
                    )
                    if (content.queuedForServer > 0) {
                        QueuedBanner(count = content.queuedForServer)
                    }
                    TabRow(selectedTabIndex = content.tab.ordinal) {
                        Tab(
                            selected = content.tab == DetailTab.ALBUMS,
                            onClick = { onSetTab(DetailTab.ALBUMS) },
                            text = { Text(stringResource(R.string.arr_lidarr_detail_tab_albums)) },
                        )
                        Tab(
                            selected = content.tab == DetailTab.HISTORY,
                            onClick = { onSetTab(DetailTab.HISTORY) },
                            text = { Text(stringResource(R.string.arr_lidarr_detail_tab_history)) },
                        )
                    }
                    if (content.tab == DetailTab.ALBUMS) {
                        AlbumsPane(
                            state = content,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onSelectAlbum = onSelectAlbum,
                            onToggleAlbumMonitored = onToggleAlbumMonitored,
                            onSearchAlbum = onSearchAlbum,
                            onOpenManualSearchAlbum = onOpenManualSearchAlbum,
                            onOpenRenamePreview = onOpenRenamePreview,
                            onOpenRetagPreview = onOpenRetagPreview,
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
        EditArtistSheet(
            form = form,
            qualityProfiles = state.qualityProfiles,
            metadataProfiles = state.metadataProfiles,
            onDismiss = onCloseEdit,
            onUpdate = onUpdateEdit,
            onSubmit = onSubmitEdit,
        )
    }
    state.fileForm?.let { form ->
        TrackFileEditorSheet(
            form = form,
            qualities = state.qualities,
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
    state.retagPreview?.let { preview ->
        RetagPreviewDialog(
            preview = preview,
            onDismiss = onCloseRetagPreview,
            onConfirm = onRunRetag,
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
        DeleteArtistDialog(
            title = state.artist?.artistName.orEmpty(),
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
private fun ArtistDetailMenu(
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onSearch: () -> Unit,
    onRename: () -> Unit,
    onRetag: () -> Unit,
    onImport: () -> Unit,
    onManualSearch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.arr_lidarr_detail_cd_more),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_refresh_metadata)) },
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            onClick = {
                open = false
                onRefreshMetadata()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_scan)) },
            leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
            onClick = {
                open = false
                onScanDisk()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = {
                open = false
                onSearch()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_manual_search)) },
            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
            onClick = {
                open = false
                onManualSearch()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_rename)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                open = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_retag)) },
            leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
            onClick = {
                open = false
                onRetag()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_import)) },
            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
            onClick = {
                open = false
                onImport()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                open = false
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_detail_menu_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                open = false
                onDelete()
            },
        )
    }
}

/** Hero art with artist name, statistics, genres, links, and monitoring state. */
@Composable
private fun ArtistHero(
    state: ArtistDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onToggleMonitored: () -> Unit,
) {
    val artist = state.artist ?: return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            val hero = ArtistDetailCoverUrls.heroUrlOf(artist, baseUrl)
            val heroCd = stringResource(
                R.string.arr_lidarr_detail_cd_hero,
                artist.artistName.orEmpty(),
            )
            if (hero != null && imageLoader != null) {
                AsyncImage(
                    model = hero,
                    imageLoader = imageLoader,
                    contentDescription = heroCd,
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
                        text = artist.artistName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val stats = artist.statistics
                    val meta = stringResource(
                        R.string.arr_lidarr_detail_meta,
                        stats?.albumCount ?: 0,
                        stats?.trackCount ?: 0,
                        Humanize.bytes(stats?.sizeOnDisk ?: 0L),
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    if (state.queuedForArtist) {
                        Text(
                            text = stringResource(R.string.arr_lidarr_detail_queued_change),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                val monitoredCd = if (artist.monitored) {
                    stringResource(
                        R.string.arr_lidarr_detail_cd_monitored,
                        artist.artistName.orEmpty(),
                    )
                } else {
                    stringResource(
                        R.string.arr_lidarr_detail_cd_not_monitored,
                        artist.artistName.orEmpty(),
                    )
                }
                IconButton(onClick = onToggleMonitored, modifier = Modifier.semantics {
                    contentDescription = monitoredCd
                }) {
                    Icon(
                        imageVector = if (artist.monitored) {
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
        val genres = artist.genres.orEmpty()
        if (genres.isNotEmpty()) {
            Text(
                text = stringResource(R.string.arr_lidarr_detail_genres, genres.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
        val links = artist.links.orEmpty().filterNot { it.url.isNullOrBlank() }
        if (links.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                links.forEach { link ->
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(link.name ?: link.url.orEmpty())
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuedBanner(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.arr_lidarr_detail_queued_banner, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * Albums grouped by release type with a width-adaptive layout: album list
 * beside track list past 720dp, expandable album cards below it.
 */
@Composable
private fun AlbumsPane(
    state: ArtistDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onSelectAlbum: (Int?) -> Unit,
    onToggleAlbumMonitored: (AlbumResource) -> Unit,
    onSearchAlbum: (Int) -> Unit,
    onOpenManualSearchAlbum: (AlbumResource) -> Unit,
    onOpenRenamePreview: (Int?) -> Unit,
    onOpenRetagPreview: (Int?) -> Unit,
    onOpenFileEditor: (TrackResource) -> Unit,
) {
    if (state.albumGroups.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.arr_lidarr_detail_albums_empty),
            message = "",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 720.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                AlbumListColumn(
                    state = state,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onSelectAlbum = onSelectAlbum,
                    onToggleAlbumMonitored = onToggleAlbumMonitored,
                    onSearchAlbum = onSearchAlbum,
                    onOpenManualSearchAlbum = onOpenManualSearchAlbum,
                    onOpenRenamePreview = onOpenRenamePreview,
                    onOpenRetagPreview = onOpenRetagPreview,
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxSize(),
                )
                val selected = state.selectedAlbumId
                if (selected != null && state.albumOf(selected) != null) {
                    TrackListPane(
                        state = state,
                        albumId = selected,
                        onOpenFileEditor = onOpenFileEditor,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxSize(),
                    )
                } else {
                    EmptyState(
                        title = stringResource(R.string.arr_lidarr_detail_tracks_empty),
                        message = "",
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxSize(),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.albumGroups.forEach { group ->
                    item(key = "group-${group.type}") {
                        AlbumGroupHeader(group = group)
                    }
                    items(group.albums.size, key = { "album-${group.albums[it].id}" }) { index ->
                        ExpandableAlbumCard(
                            album = group.albums[index],
                            state = state,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onToggleMonitored = onToggleAlbumMonitored,
                            onSearch = onSearchAlbum,
                            onOpenManualSearch = onOpenManualSearchAlbum,
                            onOpenRenamePreview = onOpenRenamePreview,
                            onOpenRetagPreview = onOpenRetagPreview,
                            onOpenFileEditor = onOpenFileEditor,
                        )
                    }
                }
            }
        }
    }
}

/** Release-type section header with the bucket count. */
@Composable
private fun AlbumGroupHeader(group: AlbumGroup) {
    val label = when (group.type) {
        AlbumReleaseType.ALBUM -> stringResource(R.string.arr_lidarr_detail_group_albums)
        AlbumReleaseType.SINGLE -> stringResource(R.string.arr_lidarr_detail_group_singles)
        AlbumReleaseType.EP -> stringResource(R.string.arr_lidarr_detail_group_eps)
        AlbumReleaseType.OTHER -> stringResource(R.string.arr_lidarr_detail_group_others)
    }
    Text(
        text = stringResource(R.string.arr_lidarr_detail_group_header, label, group.albums.size),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Tablet master column: every album grouped under its release type. */
@Composable
private fun AlbumListColumn(
    state: ArtistDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onSelectAlbum: (Int?) -> Unit,
    onToggleAlbumMonitored: (AlbumResource) -> Unit,
    onSearchAlbum: (Int) -> Unit,
    onOpenManualSearchAlbum: (AlbumResource) -> Unit,
    onOpenRenamePreview: (Int?) -> Unit,
    onOpenRetagPreview: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        state.albumGroups.forEach { group ->
            item(key = "group-${group.type}") {
                AlbumGroupHeader(group = group)
            }
            items(group.albums.size, key = { "album-${group.albums[it].id}" }) { index ->
                AlbumCard(
                    album = group.albums[index],
                    state = state,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    selected = state.selectedAlbumId == group.albums[index].id,
                    onClick = { onSelectAlbum(group.albums[index].id) },
                    onToggleMonitored = onToggleAlbumMonitored,
                    onSearch = onSearchAlbum,
                    onOpenManualSearch = onOpenManualSearchAlbum,
                    onOpenRenamePreview = onOpenRenamePreview,
                    onOpenRetagPreview = onOpenRetagPreview,
                )
            }
        }
    }
}

/** One album card: cover, monitoring toggle, statistics, quality, actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumCard(
    album: AlbumResource,
    state: ArtistDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleMonitored: (AlbumResource) -> Unit,
    onSearch: (Int) -> Unit,
    onOpenManualSearch: (AlbumResource) -> Unit,
    onOpenRenamePreview: (Int?) -> Unit,
    onOpenRetagPreview: (Int?) -> Unit,
) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            val cover = ArtistDetailCoverUrls.albumCoverUrlOf(album, baseUrl)
            val coverCd = stringResource(
                R.string.arr_lidarr_detail_cd_album_cover,
                album.title.orEmpty(),
            )
            if (cover != null && imageLoader != null) {
                AsyncImage(
                    model = cover,
                    imageLoader = imageLoader,
                    contentDescription = coverCd,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = album.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val stats = album.statistics
                val trackLine = stringResource(
                    R.string.arr_lidarr_detail_album_tracks,
                    stats?.trackFileCount ?: 0,
                    stats?.totalTrackCount ?: 0,
                )
                val size = if ((stats?.sizeOnDisk ?: 0L) > 0L) {
                    " · " + Humanize.bytes(stats?.sizeOnDisk ?: 0L)
                } else {
                    ""
                }
                Text(
                    text = trackLine + size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.profileLabel(album.profileId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AlbumActions(
                album = album,
                onSearch = onSearch,
                onOpenManualSearch = onOpenManualSearch,
                onOpenRenamePreview = onOpenRenamePreview,
                onOpenRetagPreview = onOpenRetagPreview,
                onToggleMonitored = onToggleMonitored,
            )
        }
    }
}

/** Phone layout: album card expanding in place to its track list. */
@Composable
private fun ExpandableAlbumCard(
    album: AlbumResource,
    state: ArtistDetailUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onToggleMonitored: (AlbumResource) -> Unit,
    onSearch: (Int) -> Unit,
    onOpenManualSearch: (AlbumResource) -> Unit,
    onOpenRenamePreview: (Int?) -> Unit,
    onOpenRetagPreview: (Int?) -> Unit,
    onOpenFileEditor: (TrackResource) -> Unit,
) {
    var expanded by remember(album.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        AlbumCard(
            album = album,
            state = state,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            selected = expanded,
            onClick = { expanded = !expanded },
            onToggleMonitored = onToggleMonitored,
            onSearch = onSearch,
            onOpenManualSearch = onOpenManualSearch,
            onOpenRenamePreview = onOpenRenamePreview,
            onOpenRetagPreview = onOpenRetagPreview,
        )
        if (expanded) {
            TrackRows(
                state = state,
                albumId = album.id,
                onOpenFileEditor = onOpenFileEditor,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** Search, overflow, and monitoring actions shared by both album layouts. */
@Composable
private fun AlbumActions(
    album: AlbumResource,
    onSearch: (Int) -> Unit,
    onOpenManualSearch: (AlbumResource) -> Unit,
    onOpenRenamePreview: (Int?) -> Unit,
    onOpenRetagPreview: (Int?) -> Unit,
    onToggleMonitored: (AlbumResource) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { onSearch(album.id) }) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = stringResource(
                R.string.arr_lidarr_detail_cd_search_album,
                album.title.orEmpty(),
            ),
        )
    }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(
                    R.string.arr_lidarr_detail_cd_album_more,
                    album.title.orEmpty(),
                ),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.arr_lidarr_detail_action_manual_search)) },
                leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenManualSearch(album)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.arr_lidarr_detail_action_rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenRenamePreview(album.id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.arr_lidarr_detail_action_retag)) },
                leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenRetagPreview(album.id)
                },
            )
        }
    }
    val monitoredCd = if (album.monitored) {
        stringResource(R.string.arr_lidarr_detail_cd_album_monitored, album.title.orEmpty())
    } else {
        stringResource(R.string.arr_lidarr_detail_cd_album_not_monitored, album.title.orEmpty())
    }
    IconButton(onClick = { onToggleMonitored(album) }, modifier = Modifier.semantics {
        contentDescription = monitoredCd
    }) {
        Icon(
            imageVector = if (album.monitored) {
                Icons.Filled.Visibility
            } else {
                Icons.Filled.VisibilityOff
            },
            contentDescription = null,
            tint = if (album.monitored) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Track list pane, shared by the tablet detail side and expansion bodies. */
@Composable
private fun TrackListPane(
    state: ArtistDetailUiState,
    albumId: Int,
    onOpenFileEditor: (TrackResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = state.albumOf(albumId) ?: return
    Column(modifier) {
        Text(
            text = album.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        TrackRows(state = state, albumId = albumId, onOpenFileEditor = onOpenFileEditor)
    }
}

/** Scrollable track rows of one album; non-lazy because albums are small. */
@Composable
private fun TrackRows(
    state: ArtistDetailUiState,
    albumId: Int,
    onOpenFileEditor: (TrackResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks = state.tracksOf(albumId)
    if (tracks.isEmpty()) {
        Text(
            text = stringResource(R.string.arr_lidarr_detail_tracks_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    Column(modifier) {
        tracks.forEach { track ->
            TrackRow(track = track, state = state, onOpenFileEditor = onOpenFileEditor)
        }
    }
}

/** One track row: number, title, explicit flag, duration, and file status. */
@Composable
private fun TrackRow(
    track: TrackResource,
    state: ArtistDetailUiState,
    onOpenFileEditor: (TrackResource) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = track.hasFile) { onOpenFileEditor(track) }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = track.trackNumber.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.explicit) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Explicit,
                        contentDescription = stringResource(
                            R.string.arr_lidarr_detail_cd_explicit,
                            track.title.orEmpty(),
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = trackStatusLine(track),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Humanize.clockDuration((track.duration / 1000).toLong()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (track.hasFile) {
            IconButton(onClick = { onOpenFileEditor(track) }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(
                        R.string.arr_lidarr_detail_cd_edit_file,
                        track.title.orEmpty(),
                    ),
                )
            }
        }
    }
}

/** "MP3-320 · 8.1 MB" from the embedded file, or "Missing". */
@Composable
private fun trackStatusLine(track: TrackResource): String {
    if (track.hasFile) {
        val file = track.trackFile
        val quality = file?.quality?.quality?.name
            ?.let(Humanize::qualityName)
            ?: stringResource(R.string.arr_lidarr_detail_file_title)
        return stringResource(
            R.string.arr_lidarr_detail_track_file_line,
            quality,
            Humanize.bytes(file?.size ?: 0L),
        )
    }
    return stringResource(R.string.arr_lidarr_detail_track_missing)
}

/** History tab: refresh button plus every event for the artist. */
@Composable
private fun HistoryPane(state: ArtistDetailUiState, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
        ) {
            Spacer(Modifier.weight(1f))
            if (state.historyRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(
                        R.string.arr_lidarr_detail_cd_refresh_history,
                    ),
                )
            }
        }
        if (state.history.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.arr_lidarr_detail_history_empty),
                message = "",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.history.size, key = { state.history[it].id }) { index ->
                    HistoryRow(event = state.history[index])
                }
            }
        }
    }
}

/** One history event: type badge, source title, quality, age. */
@Composable
internal fun HistoryRow(event: HistoryResource) {
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
            event.track?.title?.takeIf { it.isNotBlank() }?.let { trackTitle ->
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val quality = event.quality?.quality?.name
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
internal fun historyEventLabel(type: EntityHistoryEventType): String = when (type) {
    EntityHistoryEventType.GRABBED -> stringResource(R.string.arr_lidarr_detail_history_grabbed)
    EntityHistoryEventType.ARTIST_FOLDER_IMPORTED,
    EntityHistoryEventType.TRACK_FILE_IMPORTED,
    EntityHistoryEventType.DOWNLOAD_IMPORTED,
    -> stringResource(R.string.arr_lidarr_detail_history_imported)

    EntityHistoryEventType.DOWNLOAD_FAILED ->
        stringResource(R.string.arr_lidarr_detail_history_failed)

    EntityHistoryEventType.TRACK_FILE_DELETED ->
        stringResource(R.string.arr_lidarr_detail_history_deleted)

    EntityHistoryEventType.TRACK_FILE_RENAMED ->
        stringResource(R.string.arr_lidarr_detail_history_renamed)

    EntityHistoryEventType.TRACK_FILE_RETAGGED ->
        stringResource(R.string.arr_lidarr_detail_history_retagged)

    EntityHistoryEventType.DOWNLOAD_IGNORED ->
        stringResource(R.string.arr_lidarr_detail_history_ignored)
    EntityHistoryEventType.ALBUM_IMPORT_INCOMPLETE ->
        stringResource(R.string.arr_lidarr_detail_history_incomplete)

    EntityHistoryEventType.UNKNOWN -> stringResource(R.string.arr_lidarr_detail_history_unknown)
}

@Composable
internal fun importActionLabel(count: Int): String =
    pluralStringResource(R.plurals.arr_lidarr_detail_import_action, count, count)

/** Snackbar text for one write result; null clears the queue. */
@Composable
internal fun detailMessageText(message: DetailMessage?): String? = when (message) {
    null -> null
    DetailMessage.WriteSynced -> stringResource(R.string.arr_lidarr_detail_message_synced)
    DetailMessage.WriteQueued -> stringResource(R.string.arr_lidarr_detail_message_queued)
    DetailMessage.SearchStarted -> stringResource(R.string.arr_lidarr_detail_message_search_started)
    DetailMessage.SearchQueued -> stringResource(R.string.arr_lidarr_detail_message_search_queued)
    DetailMessage.GrabSent -> stringResource(R.string.arr_lidarr_detail_message_grabbed)
    DetailMessage.GrabQueued -> stringResource(R.string.arr_lidarr_detail_message_grab_queued)
    is DetailMessage.ImportSynced -> pluralStringResource(
        R.plurals.arr_lidarr_detail_message_imported,
        message.count,
        message.count,
    )

    DetailMessage.ImportQueued -> stringResource(R.string.arr_lidarr_detail_message_import_queued)
    DetailMessage.RenameStarted -> stringResource(R.string.arr_lidarr_detail_message_rename_started)
    DetailMessage.RenameQueued -> stringResource(R.string.arr_lidarr_detail_message_rename_queued)
    DetailMessage.RetagStarted -> stringResource(R.string.arr_lidarr_detail_message_retag_started)
    DetailMessage.RetagQueued -> stringResource(R.string.arr_lidarr_detail_message_retag_queued)
    DetailMessage.DeleteSynced -> stringResource(R.string.arr_lidarr_detail_message_delete_synced)
    DetailMessage.DeleteQueued -> stringResource(R.string.arr_lidarr_detail_message_delete_queued)
    is DetailMessage.WriteFailed -> stringResource(R.string.arr_lidarr_detail_message_failed)
}

/** LCE projection of the detail state. */
internal fun ArtistDetailUiState.lceFor(): LceState<ArtistDetailUiState> = when {
    deleted -> LceState.Empty
    loadError != null && artist == null -> LceState.Error(loadError)
    !loaded -> LceState.Loading
    artist == null -> LceState.Empty
    else -> LceState.Content(this, refreshing = refreshing)
}
