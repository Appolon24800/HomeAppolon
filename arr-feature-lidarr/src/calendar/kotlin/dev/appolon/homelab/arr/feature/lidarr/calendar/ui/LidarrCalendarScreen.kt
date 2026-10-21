package dev.appolon.homelab.arr.feature.lidarr.calendar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.calendar.data.CalendarReleaseState
import dev.appolon.homelab.arr.feature.lidarr.calendar.data.LidarrCalendarEntry
import dev.appolon.homelab.arr.feature.lidarr.calendar.data.releaseStateOf
import dev.appolon.homelab.arr.feature.lidarr.calendar.vm.LidarrCalendarDay
import dev.appolon.homelab.arr.feature.lidarr.calendar.vm.LidarrCalendarUiState
import dev.appolon.homelab.arr.feature.lidarr.calendar.vm.LidarrCalendarViewMode
import dev.appolon.homelab.arr.ui.components.CalendarDayHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Lidarr album calendar: month and agenda views at album-release
 * granularity, with the unmonitored toggle, day selection, today highlight,
 * release-state dimming, and adjacent-month prefetch driven by the
 * ViewModel. Renders through [ArrLceBox] so IO failures never crash; a wide
 * layout splits the month grid from the selected-day panel.
 */
@Composable
fun LidarrCalendarScreen(
    state: LidarrCalendarUiState,
    lce: LceState<LidarrCalendarUiState>,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onRefresh: () -> Unit,
    onViewModeChange: (LidarrCalendarViewMode) -> Unit,
    onIncludeUnmonitoredChange: (Boolean) -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    onArtistActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CalendarToolbar(
            state = state,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onGoToToday = onGoToToday,
            onViewModeChange = onViewModeChange,
            onIncludeUnmonitoredChange = onIncludeUnmonitoredChange,
        )
        ArrLceBox(
            state = lce,
            onRetry = onRefresh,
            emptyTitle = stringResource(R.string.arr_lidarr_calendar_empty_title),
            emptyMessage = stringResource(R.string.arr_lidarr_calendar_empty_message),
            modifier = Modifier.weight(1f),
        ) { content ->
            ArrPullToRefreshBox(
                isRefreshing = content.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (content.viewMode == LidarrCalendarViewMode.MONTH) {
                    MonthView(
                        state = content,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onSelectDate = onSelectDate,
                        onArtistActivate = onArtistActivate,
                    )
                } else {
                    AgendaView(
                        state = content,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onArtistActivate = onArtistActivate,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarToolbar(
    state: LidarrCalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onViewModeChange: (LidarrCalendarViewMode) -> Unit,
    onIncludeUnmonitoredChange: (Boolean) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val title = state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp),
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.arr_lidarr_calendar_cd_previous_month),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onGoToToday) {
                Icon(
                    imageVector = Icons.Filled.Today,
                    contentDescription = stringResource(R.string.arr_lidarr_calendar_cd_today),
                )
            }
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.arr_lidarr_calendar_cd_next_month),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = state.viewMode == LidarrCalendarViewMode.MONTH,
                    onClick = { onViewModeChange(LidarrCalendarViewMode.MONTH) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text(stringResource(R.string.arr_lidarr_calendar_view_month)) },
                    icon = {
                        SegmentedButtonDefaults.Icon(active = state.viewMode == LidarrCalendarViewMode.MONTH)
                    },
                )
                SegmentedButton(
                    selected = state.viewMode == LidarrCalendarViewMode.AGENDA,
                    onClick = { onViewModeChange(LidarrCalendarViewMode.AGENDA) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text(stringResource(R.string.arr_lidarr_calendar_view_agenda)) },
                    icon = {
                        SegmentedButtonDefaults.Icon(active = state.viewMode == LidarrCalendarViewMode.AGENDA)
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = state.includeUnmonitored,
                onClick = { onIncludeUnmonitoredChange(!state.includeUnmonitored) },
                label = { Text(stringResource(R.string.arr_lidarr_calendar_unmonitored)) },
                leadingIcon = if (state.includeUnmonitored) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Month grid plus the selected day's releases; wide screens split the two side by side. */
@Composable
private fun MonthView(
    state: LidarrCalendarUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onSelectDate: (LocalDate?) -> Unit,
    onArtistActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    MonthGrid(state, onSelectDate)
                }
                SelectedDayPane(
                    state = state,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onArtistActivate = onArtistActivate,
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                MonthGrid(state, onSelectDate, modifier = Modifier.padding(horizontal = 12.dp))
                SelectedDayPane(
                    state = state,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onArtistActivate = onArtistActivate,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    state: LidarrCalendarUiState,
    onSelectDate: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = modifier.fillMaxWidth()) {
        WeekdayHeader(firstDayOfWeek = state.firstDayOfWeek, locale = locale)
        state.monthCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isSelected = state.selectedDate == cell.date,
                        locale = locale,
                        onSelectDate = onSelectDate,
                        modifier = Modifier
                            .weight(1f)
                            .padding(1.dp),
                    )
                }
            }
        }
        if (state.monthIsEmpty) {
            Text(
                text = stringResource(R.string.arr_lidarr_calendar_month_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        } else if (state.selectedDate == null) {
            Text(
                text = stringResource(R.string.arr_lidarr_calendar_day_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    cell: LidarrCalendarDay,
    isSelected: Boolean,
    locale: Locale,
    onSelectDate: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val fullDate = cell.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale))
    val albums = pluralStringResource(
        R.plurals.arr_lidarr_calendar_albums_count,
        cell.entries.size,
        cell.entries.size,
    )
    val description = stringResource(R.string.arr_lidarr_calendar_cd_day, fullDate, albums)
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(
                when {
                    !cell.inMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    cell.isToday -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = { onSelectDate(cell.date) })
            .semantics { contentDescription = description },
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (cell.isToday) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else if (cell.inMonth) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(4.dp)
                .then(
                    if (cell.isToday) {
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    } else {
                        Modifier
                    },
                ),
        )
        if (cell.entries.isNotEmpty()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            ) {
                Text(
                    text = cell.entries.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** The selected day's releases under the grid, or the side panel on wide screens. */
@Composable
private fun SelectedDayPane(
    state: LidarrCalendarUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onArtistActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = state.selectedDate
    Column(modifier = modifier.fillMaxWidth()) {
        if (date == null) {
            return@Column
        }
        CalendarDayHeader(
            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            dateLabel = date.format(DateTimeFormatter.ofPattern("MMM d", locale)),
            isToday = date == state.today,
        )
        val entries = state.selectedDayEntries.orEmpty()
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.arr_lidarr_calendar_day_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )
        } else {
            entries.forEach { entry ->
                CalendarEntryRow(
                    entry = entry,
                    today = state.today,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onArtistActivate = onArtistActivate,
                )
            }
        }
    }
}

/** Agenda: one sticky [CalendarDayHeader] per day, entries sorted, capped by the ViewModel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgendaView(
    state: LidarrCalendarUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onArtistActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    if (state.agendaDays.isEmpty()) {
        Text(
            text = stringResource(R.string.arr_lidarr_calendar_agenda_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        state.agendaDays.forEach { day ->
            stickyHeader(key = "day-" + day.date) {
                CalendarDayHeader(
                    dayLabel = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    dateLabel = day.date.format(DateTimeFormatter.ofPattern("MMM d", locale)),
                    isToday = day.isToday,
                )
            }
            items(day.entries, key = { "a-" + it.albumId }) { entry ->
                CalendarEntryRow(
                    entry = entry,
                    today = state.today,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onArtistActivate = onArtistActivate,
                )
            }
        }
    }
}

/**
 * One album release: the artist as the row title, the album and its release
 * type as the subtitle, the album cover as the thumbnail. Tapping opens the
 * artist detail. Unreleased albums dim unless every track is already on
 * disk, mirroring the downloaded-vs-upcoming contrast of the episode grid.
 */
@Composable
private fun CalendarEntryRow(
    entry: LidarrCalendarEntry,
    today: LocalDate,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onArtistActivate: (Int) -> Unit,
) {
    val artist = entry.artistTitle ?: "#" + entry.artistId
    val releaseState = releaseStateOf(entry, today)
    val fullyImported = entry.trackCount > 0 && entry.trackFileCount >= entry.trackCount
    val dimmed = releaseState == CalendarReleaseState.UPCOMING && !fullyImported
    val rowDescription = stringResource(
        R.string.arr_lidarr_calendar_cd_entry,
        artist,
        entry.albumTitle.orEmpty(),
        releaseTypeLabel(entry),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .alpha(if (dimmed) 0.55f else 1f)
            .clickable(onClick = { onArtistActivate(entry.artistId) })
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = rowDescription },
    ) {
        CoverThumb(
            coverUrl = coverUrlOf(entry, baseUrl),
            contentDescription = stringResource(R.string.arr_lidarr_calendar_cd_cover, artist),
            imageLoader = imageLoader,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = artist,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.arr_lidarr_calendar_entry_subtitle,
                    entry.albumTitle ?: "",
                    releaseTypeLabel(entry),
                    releaseStateLabel(releaseState),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (entry.monitored) {
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = stringResource(R.string.arr_lidarr_calendar_cd_monitored),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (fullyImported) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.arr_lidarr_calendar_cd_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CoverThumb(
    coverUrl: String?,
    contentDescription: String?,
    imageLoader: ImageLoader?,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { this.contentDescription = contentDescription ?: "" },
    ) {
        if (coverUrl != null && imageLoader != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Absolute http URLs pass through; relative MediaCover paths resolve against the connection's base URL. */
private fun coverUrlOf(entry: LidarrCalendarEntry, baseUrl: String): String? {
    val path = entry.coverPath
    return when {
        path.isNullOrBlank() -> entry.remoteCover
        path.startsWith("http", ignoreCase = true) -> path
        else -> baseUrl.trimEnd('/') + "/" + path
    }
}

/** Primary type plus secondary types, joined the way Lidarr's UI titles a release. */
private fun releaseTypeLabel(entry: LidarrCalendarEntry): String =
    (listOfNotNull(entry.albumType) + entry.secondaryTypes).joinToString(" / ")

@Composable
private fun releaseStateLabel(state: CalendarReleaseState): String = stringResource(
    when (state) {
        CalendarReleaseState.RELEASED -> R.string.arr_lidarr_calendar_release_state_released
        CalendarReleaseState.UPCOMING -> R.string.arr_lidarr_calendar_release_state_upcoming
    },
)
