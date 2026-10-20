package dev.appolon.homelab.arr.feature.sonarr.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarEntry
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.SonarrCalendarRepository
import dev.appolon.homelab.arr.feature.sonarr.calendar.data.countUpcoming
import dev.appolon.homelab.arr.feature.sonarr.calendar.ui.SonarrCalendarScreen
import dev.appolon.homelab.arr.feature.sonarr.calendar.vm.SonarrCalendarRefreshSignal
import dev.appolon.homelab.arr.feature.sonarr.calendar.vm.SonarrCalendarViewModel
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Routes this module owns. The shell's built-in calendar slot covers
 * `server/{serverId}/sonarr/calendar`; these nested patterns add the
 * feature-owned entries `calendar/{serverId}` and the notification deep link
 * `calendar/{serverId}/day/{date}`, so a pending-intent entry point can land
 * on one specific airing day.
 */
object SonarrCalendarRoutes {
    const val OWNER = "sonarr-calendar"

    /** Calendar destination for one server, server id embedded in the path. */
    const val CALENDAR_PATTERN = "calendar/{serverId}"

    /** Deep link straight to one day; notifications target this route. */
    const val CALENDAR_DAY_PATTERN = "calendar/{serverId}/day/{date}"

    val PATTERNS = listOf(CALENDAR_PATTERN, CALENDAR_DAY_PATTERN)

    /** Registers this module's nested routes; idempotent per pattern. */
    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(OWNER, pattern) }
    }

    fun openCalendar(navigator: ArrShellNavigator, serverId: ServerId) {
        val path = fillPattern(CALENDAR_PATTERN, mapOf("serverId" to serverId.value)) ?: CALENDAR_PATTERN
        navigator.open(ArrRoute.Feature(OWNER, path))
    }

    /** Opens the calendar with [date] selected, for notification entry points. */
    fun openDay(navigator: ArrShellNavigator, serverId: ServerId, date: LocalDate) {
        val path = fillPattern(
            CALENDAR_DAY_PATTERN,
            mapOf("serverId" to serverId.value, "date" to date.toString()),
        ) ?: CALENDAR_PATTERN
        navigator.open(ArrRoute.Feature(OWNER, path))
    }

    /** Decodes one of this module's feature routes; null for other owners or shapes. */
    fun match(route: ArrRoute.Feature): Map<String, String>? {
        if (route.owner != OWNER) return null
        val segments = route.path.split('/')
        return matchRoutePattern(CALENDAR_DAY_PATTERN, segments)
            ?: matchRoutePattern(CALENDAR_PATTERN, segments)
    }
}

/** Everything the calendar screen needs from the app's singletons. */
class SonarrCalendarDeps(
    val registry: ServerRegistry,
    val secrets: ArrSecrets,
    val entityStore: EntityStore,
    val actionQueue: ActionQueue,
    val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    companion object {
        fun from(graph: ArrDataGraph) = SonarrCalendarDeps(
            registry = graph.registry,
            secrets = graph.secrets,
            entityStore = graph.entityStore,
            actionQueue = graph.actionQueue,
        )
    }
}

/**
 * Fills the shell's calendar slot for Sonarr and routes this module's nested
 * patterns to the same screen. Other apps and owners fall through to the
 * previous slot so sibling feature modules can wrap the same content object.
 */
fun ArrShellContent.withSonarrCalendar(
    deps: SonarrCalendarDeps,
    registry: ArrRouteRegistry,
): ArrShellContent {
    SonarrCalendarRoutes.register(registry)
    val previousCalendar = calendar
    val previousFeature = feature
    return ArrShellContent(
        serverList = serverList,
        serverHome = serverHome,
        library = library,
        detail = detail,
        add = add,
        queue = queue,
        calendar = { route ->
            if (route.app == AppType.SONARR) {
                SonarrCalendar(serverId = route.serverId, deps = deps)
            } else {
                previousCalendar?.invoke(route)
            }
        },
        settings = settings,
        feature = { route ->
            val params = SonarrCalendarRoutes.match(route)
            if (params != null) {
                SonarrCalendar(
                    serverId = ServerId(params.getValue("serverId")),
                    deps = deps,
                    initialSelectedDate = params["date"]?.let(::parseDay),
                )
            } else {
                previousFeature?.invoke(route)
            }
        },
    )
}

private fun parseDay(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

/**
 * Upcoming-count badge for the shell: distinct series with a monitored
 * episode airing in the next seven days, computed from the calendar cache so
 * it works offline and updates as windows refresh.
 */
fun sonarrUpcomingCountFlow(
    graph: ArrDataGraph,
    serverId: ServerId,
    today: () -> LocalDate = LocalDate::now,
    horizonDays: Int = 7,
): Flow<Int> = graph.entityStore
    .observe(serverId, SonarrCalendarRepository.ENTRY_TYPE, SonarrCalendarEntry.serializer())
    .map { entries -> countUpcoming(entries, today(), horizonDays) }

/**
 * Stateful entry: connects to the server, builds the repository over the
 * entity store, then renders the episode calendar. The connection gate keeps
 * the ViewModel constructor synchronous and fully testable. [events] carries
 * live refresh signals, normally `ArrEvent.CalendarChanged` mapped to
 * [SonarrCalendarRefreshSignal] by the integration layer. [onUpcomingCount]
 * fires whenever the next-seven-days badge number changes, for the shell to
 * hoist.
 */
@Composable
fun SonarrCalendar(
    serverId: ServerId,
    deps: SonarrCalendarDeps,
    modifier: Modifier = Modifier,
    initialSelectedDate: LocalDate? = null,
    initialIncludeUnmonitored: Boolean = false,
    events: Flow<SonarrCalendarRefreshSignal> = emptyFlow(),
    onUpcomingCount: (Int) -> Unit = {},
) {
    var apis by remember(serverId) { mutableStateOf<SonarrCalendarApisBundle?>(null) }
    LaunchedEffect(serverId) {
        apis = SonarrCalendarConnection(
            registry = deps.registry,
            secrets = deps.secrets,
            clientFactory = deps.clientFactory,
        ).connect(serverId)
    }
    val connected = apis
    if (connected == null) {
        ArrLceBox(state = LceState.Loading, modifier = modifier.fillMaxSize()) {}
        return
    }
    val repository = remember(serverId, connected) {
        SonarrCalendarRepository(
            serverId = serverId,
            store = deps.entityStore,
            calendarApi = connected.calendarApi,
        )
    }
    val vm: SonarrCalendarViewModel = viewModel(
        key = "sonarr-calendar-" + serverId.value,
    ) {
        SonarrCalendarViewModel(
            repository = repository,
            events = events,
            initialSelectedDate = initialSelectedDate,
            initialIncludeUnmonitored = initialIncludeUnmonitored,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val lce by vm.lce.collectAsStateWithLifecycle()
    val navigator = LocalArrShellNavigator.current
    val context = LocalContext.current
    LaunchedEffect(state.upcomingCount) {
        onUpcomingCount(state.upcomingCount)
    }

    SonarrCalendarScreen(
        state = state,
        lce = lce,
        baseUrl = connected.baseUrl,
        imageLoader = remember(connected) { connected.imageLoader(context) },
        onPreviousMonth = vm::previousMonth,
        onNextMonth = vm::nextMonth,
        onGoToToday = vm::goToToday,
        onRefresh = vm::refresh,
        onViewModeChange = vm::setViewMode,
        onIncludeUnmonitoredChange = vm::setIncludeUnmonitored,
        onSelectDate = vm::selectDate,
        onSeriesActivate = { seriesId ->
            navigator.open(ArrRoute.Detail(serverId, AppType.SONARR, seriesId.toString()))
        },
        modifier = modifier,
    )
}
