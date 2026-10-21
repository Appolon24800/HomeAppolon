package dev.appolon.homelab.arr.sync.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.sync.ArrEvent
import java.time.Duration

/**
 * One background pass over every registered server: run the matching
 * PollAdapter, plan notifications against the per-server channel toggles and
 * the dedupe window, post them, then rewrite the unique periodic work at the
 * adaptive interval. No Google Play Services anywhere in this path.
 */
class ArrPollWorker(
    appContext: Context,
    params: WorkerParameters,
    private val graph: ArrDataGraph,
    private val adapters: Map<AppType, PollAdapter>,
    private val poster: NotificationPoster,
    private val dedupe: NotificationDedupe,
    private val state: SyncStateStore,
    private val bounds: IntervalBounds = IntervalBounds.DEFAULT,
) : CoroutineWorker(appContext, params) {

    /**
     * WorkManager's default factory reflectively builds this constructor. A
     * process cold-started straight into a poll never ran app start, so the
     * default adapters are registered here before they are read.
     */
    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext,
        params,
        SyncBootstrap.dataGraph(appContext),
        coldStartAdapters(appContext),
        SyncBootstrap.notificationPoster(appContext),
        SyncBootstrap.notificationDedupe(),
        SyncBootstrap.stateStore(appContext),
    )

    private val planner by lazy { defaultSummaryPlanner(applicationContext) }

    override suspend fun doWork(): Result {
        val servers = try {
            graph.registry.servers()
        } catch (t: Throwable) {
            // Registry storage failed: let WorkManager backoff retry the pass
            // instead of silently stalling the schedule.
            Log.w(TAG, "Could not read server registry; retrying", t)
            return Result.retry()
        }

        var imminent = 0
        var active = 0
        var anyQuiet = false
        var anyFailed = false
        for (server in servers) {
            val adapter = adapters[server.type] ?: continue
            val result = try {
                adapter.poll(server)
            } catch (t: Throwable) {
                // The adapter contract says no throwing; one violating
                // adapter must not take down the other servers' poll.
                Log.w(TAG, "Poll adapter for ${server.type} threw", t)
                PollResult(error = ArrError.Unknown(t))
            }
            imminent += result.imminentReleases.coerceAtLeast(0)
            active += result.activeQueueItems.coerceAtLeast(0)
            when (result.outcome()) {
                PollOutcome.BUSY -> Unit
                PollOutcome.QUIET -> anyQuiet = true
                PollOutcome.FAILED -> anyFailed = true
            }
            notifyFor(server, result)
        }

        // Aggregate: any busy server pins the cadence for everyone; only a
        // pass where every polled server failed counts as a failure.
        val outcome = when {
            imminent > 0 || active > 0 -> PollOutcome.BUSY
            anyQuiet -> PollOutcome.QUIET
            anyFailed -> PollOutcome.FAILED
            else -> PollOutcome.QUIET
        }
        val previous = state.lastIntervalMillis()?.let(Duration::ofMillis)
        val next = AdaptiveInterval.nextInterval(imminent, active, outcome, previous, bounds)
        state.setLastIntervalMillis(next.toMillis())
        SyncBootstrap.applyInterval(applicationContext, next)
        return Result.success()
    }

    private suspend fun notifyFor(server: ArrServer, result: PollResult) {
        val toggles = try {
            graph.settings.notificationChannels(server.id)
        } catch (t: Throwable) {
            // Fail closed: a settings read glitch must not resurrect channels
            // the user explicitly turned off.
            Log.w(TAG, "Could not read channel toggles for ${server.id.value}", t)
            return
        }
        val planned = (result.events + planner.plan(server, result))
            .filter { it.kind.isEnabled(toggles) }
        val fresh = dedupe.filter(server.id, planned)
        if (fresh.isEmpty()) return
        val factory = NotificationFactory(applicationContext)
        for (event in fresh) {
            val posted = try {
                val id = factory.notificationId(server, event)
                poster.post(id, event.kind, factory.create(server, event))
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to post notification for ${server.id.value}", t)
                false
            }
            if (posted) {
                SyncBootstrap.emitNotification(
                    ArrEvent.NotificationIssued(
                        serverId = server.id,
                        channel = event.kind.channelId,
                        title = event.title,
                        body = event.body,
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "ArrPollWorker"
    }
}

/** Registers the process defaults, then reads the resulting adapter map. */
private fun coldStartAdapters(context: Context): Map<AppType, PollAdapter> {
    SyncBootstrap.ensureDefaultAdapters(context)
    return SyncBootstrap.adapters()
}
