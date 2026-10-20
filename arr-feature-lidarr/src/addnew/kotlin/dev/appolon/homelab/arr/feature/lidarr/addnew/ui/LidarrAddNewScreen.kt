package dev.appolon.homelab.arr.feature.lidarr.addnew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.SearchResults
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AddNewMessage
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AddNewTab
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AddNewUiState
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AlbumAddSheetUiState
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.ArtistAddSheetUiState
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.SearchScope
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The Add new screen: a search field with artist/album scope, in-library and
 * queued badges on every result, the AlbumStudio discography tab, the import
 * list browse, and the artist, album, and studio sheets. The screen owns no
 * state; the ViewModel drives it and failures land in LCE boxes.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LidarrAddNewScreen(
    state: AddNewUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onSearchSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshLists: () -> Unit,
    onTabSelect: (AddNewTab) -> Unit,
    onOpenArtistAddSheet: (ArtistResource) -> Unit,
    onOpenAlbumAddSheet: (AlbumResource) -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenStudioPicker: (ArtistResource) -> Unit,
    onStudioToggleAlbum: (Int) -> Unit,
    onStudioStrategyChange: (MonitorTypes) -> Unit,
    onStudioSelectAll: () -> Unit,
    onStudioSelectNone: () -> Unit,
    onStudioApply: () -> Unit,
    onStudioRetry: () -> Unit,
    onStudioDismiss: () -> Unit,
    onArtistSheetChange: ((ArtistAddSheetUiState) -> ArtistAddSheetUiState) -> Unit,
    onArtistSheetDismiss: () -> Unit,
    onArtistAdd: (Boolean) -> Unit,
    onAlbumSheetChange: ((AlbumAddSheetUiState) -> AlbumAddSheetUiState) -> Unit,
    onAlbumSheetDismiss: () -> Unit,
    onAlbumAdd: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    serverName: String? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { addNewMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }

    val refreshing = if (state.tab == AddNewTab.LISTS) state.listsRefreshing else false

    ArrScaffold(
        title = stringResource(R.string.arr_lidarr_addnew_title),
        subtitle = serverName,
        modifier = modifier,
        actions = {
            val refreshLabel = if (state.tab == AddNewTab.LISTS) {
                stringResource(R.string.arr_lidarr_addnew_cd_refresh_lists)
            } else {
                stringResource(R.string.arr_lidarr_addnew_cd_refresh)
            }
            IconButton(
                onClick = if (state.tab == AddNewTab.LISTS) onRefreshLists else onRefresh,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = refreshLabel)
            }
        },
        filterBar = {
            SearchField(
                query = state.query,
                scope = state.scope,
                onQueryChange = onQueryChange,
                onScopeChange = onScopeChange,
                onSearchSubmit = onSearchSubmit,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = if (state.tab == AddNewTab.LISTS) onRefreshLists else onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (state.pendingCount > 0) {
                        PendingBanner(count = state.pendingCount)
                    }
                    if (state.searchActive) {
                        SearchResultsArea(
                            state = state,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onRetry = onSearchSubmit,
                            onOpenArtistAddSheet = onOpenArtistAddSheet,
                            onOpenAlbumAddSheet = onOpenAlbumAddSheet,
                        )
                    } else {
                        TabRow(selectedTabIndex = state.tab.ordinal) {
                            AddNewTab.entries.forEach { tab ->
                                Tab(
                                    selected = state.tab == tab,
                                    onClick = { onTabSelect(tab) },
                                    text = {
                                        Text(
                                            when (tab) {
                                                AddNewTab.STUDIO -> stringResource(
                                                    R.string.arr_lidarr_addnew_tab_studio,
                                                )

                                                AddNewTab.LISTS -> stringResource(
                                                    R.string.arr_lidarr_addnew_tab_lists,
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            when (state.tab) {
                                AddNewTab.STUDIO -> StudioArea(
                                    state = state,
                                    baseUrl = baseUrl,
                                    imageLoader = imageLoader,
                                    onOpenPicker = onOpenStudioPicker,
                                    onRetry = onRefresh,
                                )

                                AddNewTab.LISTS -> ListsArea(
                                    state = state,
                                    onRetry = onRefreshLists,
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.artistSheet?.let { sheet ->
        ArtistAddSheet(
            sheet = sheet,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            rootFolders = state.rootFolders,
            qualityProfiles = state.qualityProfiles,
            metadataProfiles = state.metadataProfiles,
            tags = state.tags,
            onChange = onArtistSheetChange,
            onDismiss = onArtistSheetDismiss,
            onAdd = { onArtistAdd(false) },
            onAddAndSearch = { onArtistAdd(true) },
        )
    }

    state.albumSheet?.let { sheet ->
        AlbumAddSheet(
            sheet = sheet,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            onChange = onAlbumSheetChange,
            onDismiss = onAlbumSheetDismiss,
            onAdd = { onAlbumAdd(false) },
            onAddAndSearch = { onAlbumAdd(true) },
            onAddArtistFirst = { sheet.candidate.artist?.let(onOpenArtistAddSheet) },
        )
    }

    state.studioPicker?.let { picker ->
        StudioPickerSheet(
            picker = picker,
            baseUrl = baseUrl,
            imageLoader = imageLoader,
            onToggleAlbum = onStudioToggleAlbum,
            onStrategyChange = onStudioStrategyChange,
            onSelectAll = onStudioSelectAll,
            onSelectNone = onStudioSelectNone,
            onApply = onStudioApply,
            onRetry = onStudioRetry,
            onDismiss = onStudioDismiss,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    scope: SearchScope,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onSearchSubmit: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.arr_lidarr_addnew_search_hint)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSearchSubmit() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("addnew_search"),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            val artistsLabel = stringResource(R.string.arr_lidarr_addnew_scope_artists)
            val albumsLabel = stringResource(R.string.arr_lidarr_addnew_scope_albums)
            FilledIconToggleButton(
                checked = scope == SearchScope.ARTIST,
                onCheckedChange = { if (it) onScopeChange(SearchScope.ARTIST) },
                modifier = Modifier.semantics { contentDescription = artistsLabel },
            ) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = artistsLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconToggleButton(
                checked = scope == SearchScope.ALBUM,
                onCheckedChange = { if (it) onScopeChange(SearchScope.ALBUM) },
                modifier = Modifier.semantics { contentDescription = albumsLabel },
            ) {
                Icon(
                    Icons.Filled.DiscFull,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = albumsLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PendingBanner(count: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(R.string.arr_lidarr_addnew_pending_banner, count)) },
        leadingIcon = {
            Icon(
                Icons.Filled.CloudUpload,
                contentDescription = stringResource(
                    R.string.arr_lidarr_addnew_cd_queued_indicator,
                ),
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
    )
}

@Composable
private fun SearchResultsArea(
    state: AddNewUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onRetry: () -> Unit,
    onOpenArtistAddSheet: (ArtistResource) -> Unit,
    onOpenAlbumAddSheet: (AlbumResource) -> Unit,
) {
    val lce = state.searchLce
    if (lce == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    ArrLceBox(
        state = lce,
        onRetry = onRetry,
        emptyTitle = stringResource(R.string.arr_lidarr_addnew_search_empty_title),
        emptyMessage = stringResource(
            R.string.arr_lidarr_addnew_search_empty_message,
            state.query,
        ),
        modifier = Modifier.fillMaxSize(),
    ) { results: SearchResults ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results.artists, key = { "artist-" + it.foreignArtistId }) { artist ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    ArtistResultRow(
                        artist = artist,
                        existing = artist.foreignArtistId in state.existingForeignArtistIds,
                        queued = artist.foreignArtistId in state.queuedForeignArtistIds,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpenAddSheet = onOpenArtistAddSheet,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
            items(results.albums, key = { "album-" + it.foreignAlbumId }) { album ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    AlbumResultRow(
                        album = album,
                        existing = album.foreignAlbumId in state.existingForeignAlbumIds,
                        queued = album.foreignAlbumId in state.queuedForeignAlbumIds,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpenAddSheet = onOpenAlbumAddSheet,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistResultRow(
    artist: ArtistResource,
    existing: Boolean,
    queued: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onOpenAddSheet: (ArtistResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = artist.artistName.orEmpty().ifEmpty { artist.foreignArtistId.orEmpty() }
    val addDescription = stringResource(R.string.arr_lidarr_addnew_cd_result_add, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CoverImage(
            url = remember(artist) { AddNewLogic.artistCoverUrl(artist, baseUrl) },
            contentDescription = stringResource(
                R.string.arr_lidarr_addnew_cd_artist_cover,
                title,
            ),
            imageLoader = imageLoader,
            width = 64.dp,
            height = 64.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist.disambiguation?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            artist.genres.orEmpty().takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = stringResource(R.string.arr_lidarr_addnew_result_genres, it.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = artist.overview.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ResultTrailing(existing = existing, queued = queued, addDescription = addDescription) {
            onOpenAddSheet(artist)
        }
    }
}

@Composable
private fun AlbumResultRow(
    album: AlbumResource,
    existing: Boolean,
    queued: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onOpenAddSheet: (AlbumResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = album.title.orEmpty().ifEmpty { album.foreignAlbumId.orEmpty() }
    val artistName = album.artist?.artistName.orEmpty()
    val addDescription = stringResource(R.string.arr_lidarr_addnew_cd_result_add, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CoverImage(
            url = remember(album) { AddNewLogic.albumCoverUrl(album, baseUrl) },
            contentDescription = stringResource(
                R.string.arr_lidarr_addnew_cd_album_cover,
                title,
            ),
            imageLoader = imageLoader,
            width = 64.dp,
            height = 64.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artistName.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.arr_lidarr_addnew_result_by_artist,
                        artistName,
                        album.albumType.orEmpty().ifEmpty { "Album" },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            album.disambiguation?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = album.overview.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ResultTrailing(existing = existing, queued = queued, addDescription = addDescription) {
            onOpenAddSheet(album)
        }
    }
}

/** Existing / queued chip or the add button, shared by both result rows. */
@Composable
private fun ResultTrailing(
    existing: Boolean,
    queued: Boolean,
    addDescription: String,
    onAdd: () -> Unit,
) {
    when {
        existing -> AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(R.string.arr_lidarr_addnew_existing_chip)) },
        )

        queued -> AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(R.string.arr_lidarr_addnew_queued_chip)) },
            leadingIcon = {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        else -> IconButton(
            onClick = onAdd,
            modifier = Modifier.semantics { contentDescription = addDescription },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun StudioArea(
    state: AddNewUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onOpenPicker: (ArtistResource) -> Unit,
    onRetry: () -> Unit,
) {
    ArrLceBox(
        state = state.studioLce,
        onRetry = onRetry,
        emptyTitle = stringResource(R.string.arr_lidarr_addnew_studio_tab_empty_title),
        emptyMessage = stringResource(R.string.arr_lidarr_addnew_studio_tab_empty_message),
        modifier = Modifier.fillMaxSize(),
    ) { artists ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists, key = { it.id }) { artist ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    StudioArtistRow(
                        artist = artist,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpenPicker = onOpenPicker,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioArtistRow(
    artist: ArtistResource,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onOpenPicker: (ArtistResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = artist.artistName.orEmpty().ifEmpty { artist.foreignArtistId.orEmpty() }
    val openDescription = stringResource(
        R.string.arr_lidarr_addnew_cd_open_discography,
        title,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        CoverImage(
            url = remember(artist.id) { AddNewLogic.artistCoverUrl(artist, baseUrl) },
            contentDescription = stringResource(
                R.string.arr_lidarr_addnew_cd_artist_cover,
                title,
            ),
            imageLoader = imageLoader,
            width = 48.dp,
            height = 48.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.arr_lidarr_addnew_album_count,
                    artist.statistics?.albumCount ?: 0,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onOpenPicker(artist) },
            modifier = Modifier.semantics { contentDescription = openDescription },
        ) {
            Icon(Icons.Filled.DiscFull, contentDescription = null)
        }
    }
}

@Composable
private fun ListsArea(
    state: AddNewUiState,
    onRetry: () -> Unit,
) {
    ArrLceBox(
        state = state.listsLce,
        onRetry = onRetry,
        emptyTitle = stringResource(R.string.arr_lidarr_addnew_lists_empty_title),
        emptyMessage = stringResource(R.string.arr_lidarr_addnew_lists_empty_message),
        modifier = Modifier.fillMaxSize(),
    ) { lists ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(lists, key = { it.id }) { list ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    ImportListRow(
                        list = list,
                        qualityProfiles = state.qualityProfiles,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportListRow(
    list: ImportListResource,
    qualityProfiles: List<QualityProfileResource>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = list.name.orEmpty().ifEmpty { list.implementationName.orEmpty() },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            list.implementationName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
            }
            if (list.qualityProfileId != 0) {
                qualityProfiles.firstOrNull { it.id == list.qualityProfileId }?.name?.let { name ->
                    Text(
                        text = stringResource(
                            R.string.arr_lidarr_addnew_lists_quality,
                            name,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        if (list.enableAutomaticAdd) {
                            stringResource(R.string.arr_lidarr_addnew_lists_auto_add)
                        } else {
                            stringResource(R.string.arr_lidarr_addnew_lists_manual_add)
                        },
                    )
                },
            )
        }
    }
}

/** Snackbar text for one add-flow message, resolved where resources live. */
@Composable
internal fun addNewMessageText(message: AddNewMessage): String = when (message) {
    is AddNewMessage.ArtistAdded ->
        stringResource(R.string.arr_lidarr_addnew_notice_artist_added, message.title)

    is AddNewMessage.ArtistAddedAndSearching ->
        stringResource(
            R.string.arr_lidarr_addnew_notice_artist_added_searching,
            message.title,
        )

    is AddNewMessage.ArtistAddQueued ->
        stringResource(R.string.arr_lidarr_addnew_notice_artist_add_queued, message.title)

    is AddNewMessage.ArtistAddFailed ->
        stringResource(R.string.arr_lidarr_addnew_notice_artist_add_failed, message.title)

    is AddNewMessage.AlbumAdded ->
        stringResource(R.string.arr_lidarr_addnew_notice_album_added, message.title)

    is AddNewMessage.AlbumAddedAndSearching ->
        stringResource(
            R.string.arr_lidarr_addnew_notice_album_added_searching,
            message.title,
        )

    is AddNewMessage.AlbumAddQueued ->
        stringResource(R.string.arr_lidarr_addnew_notice_album_add_queued, message.title)

    is AddNewMessage.AlbumAddFailed ->
        stringResource(R.string.arr_lidarr_addnew_notice_album_add_failed, message.title)

    is AddNewMessage.StudioApplied ->
        stringResource(
            R.string.arr_lidarr_addnew_notice_studio_applied,
            message.count,
            message.artist,
        )

    is AddNewMessage.StudioQueued ->
        stringResource(R.string.arr_lidarr_addnew_notice_studio_queued, message.artist)

    is AddNewMessage.StudioFailed ->
        stringResource(R.string.arr_lidarr_addnew_notice_studio_failed, message.artist)
}
