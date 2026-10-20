package dev.appolon.homelab.arr.feature.radarr.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.feature.radarr.calendar.data.RadarrCalendarEntry
import dev.appolon.homelab.arr.feature.radarr.calendar.data.RadarrCalendarRepository
import dev.appolon.homelab.arr.feature.radarr.calendar.data.countUpcoming
import dev.appolon.homelab.arr.feature.radarr.calendar.ui.RadarrCalendarScreen
import dev.appolon.homelab.arr.feature.radarr.calendar.vm.CalendarRefreshSignal
import dev.appolon.homelab.arr.feature.radarr.calendar.vm.RadarrCalendarViewModel
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Shell integration for the Radarr calendar: the nested routes this module
 * owns (including the day deep link notification entry points use), open
 * helpers, the shell calendar slot, and the upcoming-count badge flow the
 * shell renders on its server card. The integration layer calls [register]
 * once before composing the shell and passes [radarrCalendarShellContent]
 * into it.
 */
object RadarrCalendarFeature {
    const val ROUTE_OWNER = "radarr-calendar"

    /** Calendar destination for one server, server id embedded in the path. */
    const val CALENDAR_PATTERN = "calendar/{serverId}"

    /** Deep link straight to one day; notifications target this route. */
    const val CALENDAR_DAY_PATTERN = "calendar/{serverId}/day/{date}"

    val PATTERNS = listOf(CALENDAR_PATTERN, CALENDAR_DAY_PATTERN)

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun openCalendar(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(CALENDAR_PATTERN, mapOf("serverId" to serverId.value)) ?: CALENDAR_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }

    /** Opens the calendar with [date] selected, for notification entry points. */
    fun openDay(navigator: ArrShellNavigator, serverId: ServerId, date: LocalDate) {
        val path = fillPattern(
            CALENDAR_DAY_PATTERN,
            mapOf("serverId" to serverId.value, "date" to date.toString()),
        ) ?: CALENDAR_PATTERN
        navigator.open(ArrRoute.Feature(ROUTE_OWNER, path))
    }

    /**
     * Upcoming-count badge for the shell: distinct monitored movies with a
     * release in the next seven days, computed from the calendar cache so it
     * works offline and updates as windows refresh.
     */
    fun upcomingCountFlow(
        graph: ArrDataGraph,
        serverId: ServerId,
        today: () -> LocalDate = LocalDate::now,
        horizonDays: Int = 7,
    ): Flow<Int> = graph.entityStore
        .observe(serverId, RadarrCalendarRepository.ENTRY_TYPE, RadarrCalendarEntry.serializer())
        .map { entries -> countUpcoming(entries.filter { it.monitored }, today(), horizonDays) }
}

/**
 * Shell content for this module: the calendar slot of `ArrRoute.Calendar`
 * for Radarr and the feature slot handling this module's nested routes.
 * Foreign feature owners fall through so a composing integration can chain
 * slots.
 */
fun radarrCalendarShellContent(graph: ArrDataGraph): ArrShellContent = ArrShellContent(
    calendar = { route ->
        if (route.app == AppType.RADARR) {
            RadarrCalendarDestination(route.serverId, graph)
        }
    },
    feature = { route -> RadarrCalendarFeatureSlot(route, graph) },
)

/**
 * The release calendar of one Radarr server, wired to the [ArrDataGraph]
 * singletons. The connection resolves asynchronously (registry read plus key
 * decryption); a spinner covers that gap, then the ViewModel builds on the
 * connected API bundle and the authenticated Coil loader. [events] carries
 * live refresh signals, normally `ArrEvent.CalendarChanged` mapped to
 * [CalendarRefreshSignal] by the integration layer. [onUpcomingCount] fires
 * whenever the next-seven-days badge number changes, for the shell to hoist.
 */
@Composable
fun RadarrCalendarDestination(
    serverId: ServerId,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    initialSelectedDate: LocalDate? = null,
    initialIncludeUnmonitored: Boolean = false,
    events: Flow<CalendarRefreshSignal> = emptyFlow(),
    onUpcomingCount: (Int) -> Unit = {},
) {
    val connection = remember(serverId) {
        RadarrCalendarConnection(graph.registry, graph.secrets)
    }
    var connected by remember(serverId) { mutableStateOf<RadarrCalendarBundle?>(null) }
    LaunchedEffect(serverId) {
        connected = connection.connect(serverId)
    }
    val apis = connected
    if (apis == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val context = LocalContext.current
    val imageLoader = remember(apis) { apis.imageLoader(context) }
    val vm: RadarrCalendarViewModel = viewModel(key = "radarr-calendar-" + serverId.value) {
        RadarrCalendarViewModel(
            repository = RadarrCalendarRepository(
                serverId = serverId,
                store = graph.entityStore,
                calendarApi = apis.calendarApi,
            ),
            events = events,
            initialSelectedDate = initialSelectedDate,
            initialIncludeUnmonitored = initialIncludeUnmonitored,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val navigator = LocalArrShellNavigator.current
    LaunchedEffect(state.upcomingCount) {
        onUpcomingCount(state.upcomingCount)
    }

    RadarrCalendarScreen(
        state = state,
        lce = lce,
        baseUrl = apis.baseUrl,
        imageLoader = imageLoader,
        onPreviousMonth = vm::previousMonth,
        onNextMonth = vm::nextMonth,
        onGoToToday = vm::goToToday,
        onRefresh = vm::refresh,
        onViewModeChange = vm::setViewMode,
        onIncludeUnmonitoredChange = vm::setIncludeUnmonitored,
        onSelectDate = vm::selectDate,
        onMovieActivate = { movieId ->
            navigator.open(ArrRoute.Detail(serverId, AppType.RADARR, movieId.toString()))
        },
        modifier = modifier,
    )
}

/**
 * Feature-slot entry: renders this module's nested routes and defers to the
 * caller for foreign owners by rendering nothing, so the integration layer
 * can stack several feature slots in one shell content.
 */
@Composable
fun RadarrCalendarFeatureSlot(
    route: ArrRoute.Feature,
    graph: ArrDataGraph,
    modifier: Modifier = Modifier,
    events: Flow<CalendarRefreshSignal> = emptyFlow(),
    onUpcomingCount: (Int) -> Unit = {},
) {
    if (route.owner != RadarrCalendarFeature.ROUTE_OWNER) return
    val segments = route.path.split('/')
    val day = matchRoutePattern(RadarrCalendarFeature.CALENDAR_DAY_PATTERN, segments)
    val base = matchRoutePattern(RadarrCalendarFeature.CALENDAR_PATTERN, segments)
    val initialDate = day?.get("date")
        ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
    when {
        day != null -> RadarrCalendarDestination(
            serverId = ServerId(day.getValue("serverId")),
            graph = graph,
            modifier = modifier,
            initialSelectedDate = initialDate,
            events = events,
            onUpcomingCount = onUpcomingCount,
        )

        base != null -> RadarrCalendarDestination(
            serverId = ServerId(base.getValue("serverId")),
            graph = graph,
            modifier = modifier,
            events = events,
            onUpcomingCount = onUpcomingCount,
        )
    }
}
