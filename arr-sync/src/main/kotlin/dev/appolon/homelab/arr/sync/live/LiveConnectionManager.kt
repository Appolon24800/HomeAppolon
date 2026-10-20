package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.sync.ArrEvent
import dev.appolon.homelab.arr.sync.LiveConnectionState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns one live connection per configured server and fans every event onto a
 * single [events] flow (replay 0). The foreground lifecycle calls [start] and
 * [stop]; call [refresh] after the server list or a server's endpoint fields
 * change, and the manager swaps exactly the connections whose [LiveServerSpec]
 * changed. Bursts are conflated: everything arriving within
 * [conflateWindowMillis] of a burst's first event collapses to one event per
 * conflation key, keeping the last write per key, which turns a hub event
 * storm into one refresh per entity type.
 *
 * Reconnects after connectivity returns come from [connectivity]: every
 * connection not reporting CONNECTED is stopped and started when the flow
 * flips back to true (transitions only, so a chatty listener cannot churn
 * sockets).
 */
class LiveConnectionManager(
    private val scope: CoroutineScope,
    private val connectivity: Flow<Boolean>,
    private val specs: suspend () -> List<LiveServerSpec>,
    private val connectionFactory: LiveConnectionFactory,
    private val conflateWindowMillis: Long = CONFLATE_WINDOW_MILLIS,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(
        scope: CoroutineScope,
        connectivity: Flow<Boolean>,
        specs: suspend () -> List<LiveServerSpec>,
        clientFactory: ArrClientFactory,
        conflateWindowMillis: Long = CONFLATE_WINDOW_MILLIS,
        clockMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        scope,
        connectivity,
        specs,
        defaultConnectionFactory(scope, clientFactory),
        conflateWindowMillis,
        clockMillis,
    )

    private val _events = MutableSharedFlow<ArrEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
    )
    private val _states = MutableStateFlow<Map<ServerId, LiveConnectionState>>(emptyMap())

    /** Every live event from every server, replay 0, conflated per burst. */
    val events: SharedFlow<ArrEvent> = _events.asSharedFlow()

    /** Live connection state per server; servers appear here once started. */
    val states: StateFlow<Map<ServerId, LiveConnectionState>> = _states.asStateFlow()

    private data class Managed(
        val spec: LiveServerSpec,
        val connection: LiveConnection,
        val pump: Job,
        val watch: Job,
    )

    private val inflight = Channel<ArrEvent>(Channel.UNLIMITED)
    private val connections = mutableMapOf<ServerId, Managed>()
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)
    private val notificationSeq = AtomicLong()
    private var managerJob: Job? = null

    init {
        require(conflateWindowMillis > 0) { "conflateWindowMillis must be positive" }
    }

    /** Idempotent: starts the conflation gate, connectivity watcher, and first refresh. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        managerJob = scope.launch {
            launch { conflationLoop() }
            launch { connectivityLoop() }
            refresh()
        }
    }

    /**
     * Reconciles connections with [specs]: stops connections for removed or
     * edited servers, starts connections for added ones. Safe to call anytime.
     */
    suspend fun refresh() {
        if (!started.get()) return
        val desired = specs().associateBy(LiveServerSpec::serverId)
        val stale = mutableListOf<Managed>()
        val fresh = mutableListOf<LiveServerSpec>()
        mutex.withLock {
            val iterator = connections.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val wanted = desired[entry.key]
                if (wanted != entry.value.spec) {
                    // Null means removed; any other mismatch means the endpoint
                    // (key, auth, proxy, trust) changed and needs a new socket.
                    stale += entry.value
                    iterator.remove()
                }
            }
            desired.values.forEach { spec ->
                if (spec.serverId !in connections) fresh += spec
            }
        }
        // Stopping can suspend on network teardown; keep it out of the lock so
        // event pumps and state watchers keep making progress.
        stale.forEach { managed -> teardown(managed) }
        fresh.forEach { spec -> install(spec) }
    }

    /** Stops every connection and the gate; [start] may be called again later. */
    suspend fun stop() {
        if (!started.compareAndSet(true, false)) return
        managerJob?.cancel()
        val all = mutex.withLock {
            val copy = connections.values.toList()
            connections.clear()
            copy
        }
        all.forEach { managed -> teardown(managed) }
        _states.value = emptyMap()
    }

    private suspend fun install(spec: LiveServerSpec) {
        val connection = connectionFactory.create(spec)
        val pump = scope.launch {
            connection.events.collect { event -> inflight.send(event) }
        }
        val watch = scope.launch {
            connection.state.collect { state ->
                _states.update { current -> current + (spec.serverId to state) }
            }
        }
        mutex.withLock { connections[spec.serverId] = Managed(spec, connection, pump, watch) }
        connection.start()
    }

    private suspend fun teardown(managed: Managed) {
        managed.pump.cancel()
        managed.watch.cancel()
        withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { managed.connection.stop() }
        _states.update { current -> current - managed.spec.serverId }
    }

    /**
     * The conflation gate. The window anchors to the burst's first event, not
     * to quiet, so a steady trickle of queue updates cannot starve the entity
     * events queued behind it. Arrival order of first writes is the emit order.
     */
    private suspend fun conflationLoop() {
        val pending = LinkedHashMap<String, ArrEvent>()
        while (currentCoroutineContext().isActive) {
            val first = inflight.receive()
            pending[conflationKey(first)] = first
            val windowEnd = clockMillis() + conflateWindowMillis
            while (true) {
                val remaining = windowEnd - clockMillis()
                if (remaining <= 0) break
                val next = withTimeoutOrNull(remaining) { inflight.receive() } ?: break
                pending[conflationKey(next)] = next
            }
            pending.values.forEach { event -> _events.emit(event) }
            pending.clear()
        }
    }

    private fun conflationKey(event: ArrEvent): String = when (event) {
        // Notifications are user-facing messages, not cache invalidations.
        // Collapsing them would silently drop alerts, so every one is unique.
        is ArrEvent.NotificationIssued -> "notification:" + notificationSeq.incrementAndGet()
        is ArrEvent.EntitiesChanged -> "entities:" + event.entity
        is ArrEvent.QueueChanged -> "queue"
        is ArrEvent.CalendarChanged -> "calendar"
        is ArrEvent.HealthChanged -> "health"
        is ArrEvent.DiskSpaceChanged -> "diskspace"
    }

    private suspend fun connectivityLoop() {
        connectivity.distinctUntilChanged().collect { online ->
            if (!online) return@collect
            val stalled = mutex.withLock {
                connections.values
                    .filter { it.connection.state.value != LiveConnectionState.CONNECTED }
                    .map { it.connection }
            }
            stalled.forEach { connection ->
                connection.stop()
                connection.start()
            }
        }
    }

    private companion object {
        const val CONFLATE_WINDOW_MILLIS = 250L

        /** A wedged socket must not hold the whole stop() chain hostage. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val EVENT_BUFFER_CAPACITY = 256
    }
}

/**
 * The production factory: a [SignalRLiveConnection] per Servarr server and a
 * [BazarrLiveConnection] per Bazarr server, both riding the per-server OkHttp
 * client from [clientFactory] so proxies, TLS policies, and auth match the
 * REST path.
 */
fun defaultConnectionFactory(
    scope: CoroutineScope,
    clientFactory: ArrClientFactory,
): LiveConnectionFactory = LiveConnectionFactory { spec ->
    val client = clientFactory.clientFor(spec.endpoint)
    when (spec.appType) {
        AppType.BAZARR -> BazarrLiveConnection(spec, scope, client)
        else -> SignalRLiveConnection(spec, scope, client)
    }
}
