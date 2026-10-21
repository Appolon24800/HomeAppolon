package dev.appolon.homelab.arr.feature.radarr.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import okhttp3.OkHttpClient
import java.util.Locale
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The movie detail screen: hero art, monitored toggle, overview, cast, files
 * with quality, history, extra files, and the write actions (rename preview,
 * search and grab, interactive import entry, edit, delete). Wide layouts
 * split the page into an art/overview pane and a data pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrMovieDetailScreen(
    state: RadarrMovieDetailUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onToggleMonitored: () -> Unit,
    onSaveEdit: (EditableMovieUi, Boolean) -> Unit,
    onDelete: (Boolean, Boolean) -> Unit,
    onRename: () -> Unit,
    onSearch: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val movie = (state.movie as? LceState.Content)?.data
    val imageLoader = rememberRadarrImageLoader(state.imageClient)

    ArrScaffold(
        title = movie?.title.orEmpty().ifBlank {
            stringResource(R.string.arr_radarr_detail_title_fallback)
        },
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { showEdit = true }, enabled = movie != null) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.arr_radarr_detail_cd_edit),
                )
            }
            IconButton(onClick = { showDelete = true }, enabled = movie != null) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.arr_radarr_detail_cd_delete),
                )
            }
        },
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrLceBox(
                state = state.movie,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            ) { loaded ->
                DetailPanes(
                    state = state,
                    movie = loaded,
                    imageLoader = imageLoader,
                    onToggleMonitored = onToggleMonitored,
                    onRename = { showRename = true },
                    onSearch = onSearch,
                    onOpenImport = onOpenImport,
                )
            }
        }
    }

    if (showEdit && movie != null) {
        RadarrMovieEditSheet(
            movie = movie,
            options = state.editOptions,
            working = state.working,
            onSave = { edits, moveFiles ->
                showEdit = false
                onSaveEdit(edits, moveFiles)
            },
            onDismiss = { showEdit = false },
        )
    }
    if (showDelete && movie != null) {
        RadarrMovieDeleteDialog(
            title = movie.title,
            hasFiles = movie.hasFile,
            working = state.working,
            onConfirm = { deleteFiles, addExclusion ->
                showDelete = false
                onDelete(deleteFiles, addExclusion)
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showRename) {
        RadarrMovieRenameDialog(
            files = (state.files as? LceState.Content)?.data.orEmpty(),
            working = state.working,
            onConfirm = {
                showRename = false
                onRename()
            },
            onDismiss = { showRename = false },
        )
    }
}

/** Two panes on tablets, one scrolling column on phones. */
@Composable
private fun DetailPanes(
    state: RadarrMovieDetailUiState,
    movie: MovieDetailUi,
    imageLoader: ImageLoader?,
    onToggleMonitored: () -> Unit,
    onRename: () -> Unit,
    onSearch: () -> Unit,
    onOpenImport: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    DetailBody(
                        state = state,
                        movie = movie,
                        imageLoader = imageLoader,
                        dataSections = false,
                        onToggleMonitored = onToggleMonitored,
                        onRename = onRename,
                        onSearch = onSearch,
                        onOpenImport = onOpenImport,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    DetailBody(
                        state = state,
                        movie = movie,
                        imageLoader = imageLoader,
                        dataSections = true,
                        onToggleMonitored = onToggleMonitored,
                        onRename = onRename,
                        onSearch = onSearch,
                        onOpenImport = onOpenImport,
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailBody(
                    state = state,
                    movie = movie,
                    imageLoader = imageLoader,
                    dataSections = null,
                    onToggleMonitored = onToggleMonitored,
                    onRename = onRename,
                    onSearch = onSearch,
                    onOpenImport = onOpenImport,
                )
            }
        }
    }
}

/** Header + overview + cast (when [dataSections] is false), data (when true). */
@Composable
private fun DetailBody(
    state: RadarrMovieDetailUiState,
    movie: MovieDetailUi,
    imageLoader: ImageLoader?,
    dataSections: Boolean?,
    onToggleMonitored: () -> Unit,
    onRename: () -> Unit,
    onSearch: () -> Unit,
    onOpenImport: () -> Unit,
) {
    if (dataSections != true) {
        if (state.anyQueued) QueuedBanner(state.queuedActions.size)
        MovieHero(movie, imageLoader)
        MovieFactsRow(state, movie)
        LabeledSwitch(
            label = stringResource(ArrUiR.string.arr_common_monitored),
            checked = movie.monitored,
            onCheckedChange = { onToggleMonitored() },
            supportingText = stringResource(R.string.arr_radarr_detail_monitored_support),
        )
        OverviewSection(movie)
        CastSection(state, imageLoader, crew = false)
        CastSection(state, imageLoader, crew = true)
    }
    if (dataSections == null || dataSections == true) {
        ActionRow(
            state = state,
            onSearch = onSearch,
            onRename = onRename,
            onOpenImport = onOpenImport,
        )
        FilesSection(state)
        ExtraFilesSection(state)
        HistorySection(state)
    }
}

/** "N changes waiting to sync" banner over offline writes. */
@Composable
private fun QueuedBanner(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(ArrUiR.string.arr_common_system_pending_banner, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Backdrop with scrim, poster thumbnail, title, year, and runtime. */
@Composable
private fun MovieHero(movie: MovieDetailUi, imageLoader: ImageLoader?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val backdropDescription =
            stringResource(R.string.arr_radarr_detail_cd_backdrop, movie.title)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (movie.backdropUrl != null && imageLoader != null) {
                AsyncImage(
                    model = movie.backdropUrl,
                    contentDescription = backdropDescription,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        ),
                    ),
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            val posterShape = RoundedCornerShape(10.dp)
            val posterDescription =
                stringResource(ArrUiR.string.arr_common_addnew_cd_poster, movie.title)
            Box(
                Modifier
                    .size(width = 92.dp, height = 138.dp)
                    .clip(posterShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                if (movie.posterUrl != null && imageLoader != null) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = posterDescription,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = posterDescription,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HeroOverlayText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movie.year > 0) {
                    Text(
                        text = stringResource(
                            R.string.arr_radarr_detail_subtitle,
                            movie.year,
                            Humanize.duration(movie.runtimeMinutes * 60L),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeroOverlayText.copy(alpha = 0.85f),
                    )
                }
                if (movie.collectionTitle != null) {
                    Text(
                        text = stringResource(
                            R.string.arr_radarr_detail_collection,
                            movie.collectionTitle,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = HeroOverlayText.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

private val HeroOverlayText = Color(0xFFF2F2F2)

/** Chips carrying the headline facts: ratings, certification, studio, profile. */
@Composable
private fun MovieFactsRow(state: RadarrMovieDetailUiState, movie: MovieDetailUi) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            movie.imdbRating?.let {
                AssistChip(onClick = {}, label = {
                    Text(stringResource(R.string.arr_radarr_detail_rating_imdb, formatRating(it)))
                })
            }
            movie.tmdbRating?.let {
                AssistChip(onClick = {}, label = {
                    Text(stringResource(R.string.arr_radarr_detail_rating_tmdb, formatRating(it)))
                })
            }
            movie.certification?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }
            movie.studio?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }
            AssistChip(onClick = {}, label = {
                Text(
                    state.editOptions.qualityProfileName(movie.qualityProfileId)
                        ?: stringResource(
                            ArrUiR.string.arr_common_quality_delay_profile_title,
                            movie.qualityProfileId,
                        ),
                )
            })
        }
        movie.path?.let {
            Text(
                text = stringResource(R.string.arr_radarr_detail_path_value, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ReleaseDates(movie)
    }
}

@Composable
private fun ReleaseDates(movie: MovieDetailUi) {
    val dates = listOfNotNull(
        movie.inCinemas?.let {
            stringResource(R.string.arr_radarr_addnew_availability_in_cinemas) to ArrTime.dateTimeShort(it)
        },
        movie.digitalRelease?.let {
            stringResource(R.string.arr_radarr_detail_digital_release) to ArrTime.dateTimeShort(it)
        },
        movie.physicalRelease?.let {
            stringResource(R.string.arr_radarr_detail_physical_release) to
                ArrTime.dateTimeShort(it)
        },
    )
    if (dates.isEmpty()) return
    Text(
        text = dates.joinToString("  ·  ") { (label, value) -> "$label $value" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun OverviewSection(movie: MovieDetailUi) {
    SectionHeader(title = stringResource(R.string.arr_radarr_detail_overview))
    Text(
        text = movie.overview ?: stringResource(R.string.arr_radarr_detail_no_overview),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (movie.genres.isNotEmpty()) {
        Text(
            text = stringResource(R.string.arr_radarr_detail_genres),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            movie.genres.forEach { genre ->
                AssistChip(onClick = {}, label = { Text(genre) })
            }
        }
    }
    if (movie.alternativeTitles.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.arr_radarr_detail_alt_titles,
                movie.alternativeTitles.joinToString(", "),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** Cast row or crew row. */
@Composable
private fun CastSection(
    state: RadarrMovieDetailUiState,
    imageLoader: ImageLoader?,
    crew: Boolean,
) {
    val section = if (crew) state.crew else state.cast
    val title = stringResource(
        if (crew) R.string.arr_radarr_detail_crew else R.string.arr_radarr_detail_cast,
    )
    when (section) {
        is LceState.Content -> if (section.data.isNotEmpty()) {
            SectionHeader(title = title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(
                    section.data,
                    key = { "${if (crew) "c" else "a"}-${it.id}" },
                    contentType = { "credit" },
                ) { credit ->
                    CreditTile(credit, imageLoader)
                }
            }
        }
        is LceState.Error -> SectionError(title, section.error)
        else -> Unit
    }
}

@Composable
private fun SectionError(title: String, error: ArrError) {
    SectionHeader(title = title)
    Text(
        text = arrErrorMessage(error),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun CreditTile(credit: CreditUi, imageLoader: ImageLoader?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp),
    ) {
        val shape = RoundedCornerShape(48.dp)
        val headshotDescription = stringResource(
            R.string.arr_radarr_detail_cd_headshot,
            credit.name,
        )
        Box(
            Modifier
                .size(96.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (credit.headshotUrl != null && imageLoader != null) {
                AsyncImage(
                    model = credit.headshotUrl,
                    contentDescription = headshotDescription,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = headshotDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = credit.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = credit.character ?: credit.job.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Primary write actions: search and grab, rename preview, import entry. */
@Composable
private fun ActionRow(
    state: RadarrMovieDetailUiState,
    onSearch: () -> Unit,
    onRename: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val hasFiles = (state.files as? LceState.Content)?.data?.isNotEmpty() == true
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(onClick = onSearch, enabled = !state.working) {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(ArrUiR.string.arr_common_indexers_mode_search))
        }
        OutlinedButton(onClick = onRename, enabled = !state.working && hasFiles) {
            Icon(imageVector = Icons.Filled.DriveFileRenameOutline, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.arr_radarr_detail_rename))
        }
        OutlinedButton(onClick = onOpenImport) {
            Icon(imageVector = Icons.Filled.Input, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(ArrUiR.string.arr_common_import_flow_approve))
        }
    }
}

@Composable
private fun FilesSection(state: RadarrMovieDetailUiState) {
    when (val files = state.files) {
        is LceState.Content -> {
            SectionHeader(title = stringResource(ArrUiR.string.arr_common_import_flow_files_label))
            if (files.data.isEmpty()) {
                SectionEmpty(R.string.arr_radarr_detail_files_none)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    files.data.forEach { file -> MovieFileCard(file) }
                }
            }
        }
        is LceState.Error -> SectionError(
            stringResource(ArrUiR.string.arr_common_import_flow_files_label),
            files.error,
        )
        else -> Unit
    }
}

@Composable
private fun MovieFileCard(file: MovieFileUi) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.relativePath,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (file.qualityCutoffNotMet) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.arr_radarr_detail_cutoff_not_met)) },
                    )
                }
            }
            val qualityLine = listOfNotNull(
                file.qualityName?.let(Humanize::qualityName),
                file.languages.takeIf { it.isNotBlank() },
                file.edition,
                file.releaseGroup,
                file.mediaSummary,
                Humanize.bytes(file.sizeBytes),
                file.dateAdded?.let { ArrTime.relative(it) },
            ).joinToString(" · ")
            Text(
                text = qualityLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (file.customFormats.isNotBlank()) {
                Text(
                    text = file.customFormats +
                        (file.customFormatScore?.let { " ($it)" }.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ExtraFilesSection(state: RadarrMovieDetailUiState) {
    when (val extras = state.extraFiles) {
        is LceState.Content -> {
            SectionHeader(title = stringResource(R.string.arr_radarr_detail_extra_files))
            if (extras.data.isEmpty()) {
                SectionEmpty(R.string.arr_radarr_detail_extra_files_none)
            } else {
                extras.data.forEach { extra ->
                    ListItem(
                        headlineContent = { Text(extra.relativePath) },
                        supportingContent = {
                            val details = listOfNotNull(
                                extra.typeName?.lowercase(Locale.ROOT),
                                extra.languageTags.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (details.isNotBlank()) Text(details)
                        },
                    )
                }
            }
        }
        is LceState.Error -> SectionError(
            stringResource(R.string.arr_radarr_detail_extra_files),
            extras.error,
        )
        else -> Unit
    }
}

@Composable
private fun HistorySection(state: RadarrMovieDetailUiState) {
    when (val history = state.history) {
        is LceState.Content -> {
            SectionHeader(title = stringResource(ArrUiR.string.arr_common_history_title))
            if (history.data.isEmpty()) {
                SectionEmpty(R.string.arr_radarr_detail_history_none)
            } else {
                history.data.forEach { event ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = historyIcon(event.eventType),
                                contentDescription = historyEventLabel(event.eventType),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = event.sourceTitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        supportingContent = {
                            val details = listOfNotNull(
                                historyEventLabel(event.eventType),
                                event.qualityName?.let(Humanize::qualityName),
                                event.languages.takeIf { it.isNotBlank() },
                                event.date?.let { ArrTime.relative(it) },
                            ).joinToString(" · ")
                            if (details.isNotBlank()) Text(details)
                            event.message?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        }
        is LceState.Error -> SectionError(
            stringResource(ArrUiR.string.arr_common_history_title),
            history.error,
        )
        else -> Unit
    }
}

@Composable
private fun SectionEmpty(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Rate as "7.4" / "8" without locale digit surprises. */
private fun formatRating(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }

private fun historyIcon(eventType: String): ImageVector = when (eventType) {
    "GRABBED" -> Icons.Filled.Download
    "DOWNLOAD_FOLDER_IMPORTED", "MOVIE_FOLDER_IMPORTED" -> Icons.Filled.CheckCircle
    "DOWNLOAD_FAILED" -> Icons.Filled.Error
    "MOVIE_FILE_DELETED" -> Icons.Filled.DeleteOutline
    "MOVIE_FILE_RENAMED" -> Icons.Filled.DriveFileRenameOutline
    "DOWNLOAD_IGNORED" -> Icons.Filled.Block
    else -> Icons.Filled.Schedule
}

@Composable
internal fun historyEventLabel(eventType: String): String = stringResource(
    when (eventType) {
        "GRABBED" -> ArrUiR.string.arr_common_history_filter_grabbed
        "DOWNLOAD_FOLDER_IMPORTED" -> R.string.arr_radarr_detail_event_folder_imported
        "MOVIE_FOLDER_IMPORTED" -> R.string.arr_radarr_detail_event_movie_imported
        "DOWNLOAD_FAILED" -> ArrUiR.string.arr_common_detail_event_failed
        "MOVIE_FILE_DELETED" -> ArrUiR.string.arr_common_detail_event_file_deleted
        "MOVIE_FILE_RENAMED" -> ArrUiR.string.arr_common_history_filter_renamed
        "DOWNLOAD_IGNORED" -> ArrUiR.string.arr_common_history_type_ignored
        else -> ArrUiR.string.arr_common_detail_event_unknown
    },
)

/**
 * One Coil loader per server client: the okhttp fetcher it installs is the
 * only way Coil 3 loads network imagery, and the client carries the server's
 * auth headers scoped to its own host.
 */
@Composable
internal fun rememberRadarrImageLoader(client: OkHttpClient?): ImageLoader? {
    if (client == null) return null
    val context = LocalContext.current
    return remember(client) {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .crossfade(true)
            .build()
    }
}
