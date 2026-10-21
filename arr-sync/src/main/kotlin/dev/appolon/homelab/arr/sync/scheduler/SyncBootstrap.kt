package dev.appolon.homelab.arr.sync.scheduler

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.data.ArrDataGraph
import dev.appolon.homelab.arr.sync.ArrEvent
import java.io.File
import java.time.Duration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/**
 * Entrypoint for background sync. Registers per-app [PollAdapter]s, creates
 * the notification channels, and schedules the unique periodic poll work.
 * Safe to call repeatedly: the in-process flag short-circuits and KEEP leaves
 * an existing schedule untouched, so nothing duplicates.
 *
 * This object is also the process-wide holder of the [ArrDataGraph] and sync
 * state DataStore used by the worker, because DataStore tolerates a single
 * writer per file. Callers building feature screens should route through
 * [dataGraph] rather than constructing their own.
 */
object SyncBootstrap {

    const val UNIQUE_PERIODIC_NAME = "arr_background_poll"

    private val adaptersByType = LinkedHashMap<AppType, PollAdapter>()

    /**
     * Notifications the scheduler issued, shaped for the shared ArrEvent
     * fan-out. Emissions are best effort: slow collectors drop, they can
     * always read the posted notification instead.
     */
    private val notificationBus = MutableSharedFlow<ArrEvent.NotificationIssued>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notificationEvents: SharedFlow<ArrEvent.NotificationIssued> get() = notificationBus

    @Volatile private var dataGraph: ArrDataGraph? = null
    @Volatile private var syncState: SyncStateStore? = null
    @Volatile private var dedupe: NotificationDedupe? = null
    @Volatile private var scheduled = false
    @Volatile private var defaultAdapterRegistrar: (() -> Unit)? = null

    fun registerAdapter(type: AppType, adapter: PollAdapter) {
        synchronized(adaptersByType) { adaptersByType[type] = adapter }
    }

    fun adapters(): Map<AppType, PollAdapter> =
        synchronized(adaptersByType) { adaptersByType.toMap() }

    /**
     * Installs the registrar that builds the five default per-app adapters.
     * :app owns the adapter classes (they live in the feature modules), so it
     * supplies this hook; the worker leans on it when its process never ran
     * app start. Pass null to clear.
     */
    fun installDefaultAdapterRegistrar(registrar: (() -> Unit)?) {
        defaultAdapterRegistrar = registrar
    }

    /**
     * Registers the default adapters when none are registered, so a worker
     * built in a cold process still polls. Prefers the registrar :app
     * installed; when even that has not run (a process WorkManager started
     * straight into a poll), resolves it reflectively through the :app
     * bootstrap object, whose name the `@Keep` annotation pins. Registration
     * is idempotent, so concurrent paths converge.
     */
    fun ensureDefaultAdapters(context: Context) {
        synchronized(adaptersByType) {
            if (adaptersByType.isNotEmpty()) return
        }
        defaultAdapterRegistrar?.let { registrar ->
            registrar()
            return
        }
        runCatching {
            Class.forName(APP_BOOTSTRAP_CLASS)
                .getMethod("registerSyncAdaptersForColdStart", Context::class.java)
                .invoke(null, context.applicationContext)
        }
    }

    /** Call from app startup; repeat calls are no-ops. */
    fun initialize(context: Context, defaultInterval: Duration = IntervalBounds.DEFAULT_INTERVAL) {
        if (scheduled) return
        synchronized(this) {
            if (scheduled) return
            val app = context.applicationContext
            val manager = app.getSystemService(NotificationManager::class.java)
            if (manager != null) {
                ArrNotificationChannels.ensureCreated(app, manager)
            }
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork(defaultInterval),
            )
            scheduled = true
        }
    }

    /** Process-singleton graph for the sync path; see the class KDoc. */
    fun dataGraph(context: Context): ArrDataGraph =
        dataGraph ?: synchronized(this) {
            dataGraph ?: ArrDataGraph(context.applicationContext).also { dataGraph = it }
        }

    internal fun notificationDedupe(): NotificationDedupe =
        dedupe ?: synchronized(this) {
            dedupe ?: NotificationDedupe().also { dedupe = it }
        }

    internal fun notificationPoster(context: Context): NotificationPoster {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        requireNotNull(manager) { "NotificationManager is always present on Android" }
        return AndroidNotificationPoster(manager)
    }

    internal fun stateStore(context: Context): SyncStateStore =
        syncState ?: synchronized(this) {
            syncState ?: DataStoreSyncStateStore(
                PreferenceDataStoreFactory.create(
                    produceFile = { File(context.applicationContext.filesDir, STATE_FILE_NAME) },
                ),
            ).also { syncState = it }
        }

    /**
     * Rewrites the unique periodic work at the adaptive interval. Never
     * resurrects a schedule the user cancelled; if the liveness query fails
     * the existing spec simply stays in place for another period.
     */
    internal fun applyInterval(context: Context, interval: Duration) {
        val workManager = WorkManager.getInstance(context)
        val alive = try {
            workManager.getWorkInfosForUniqueWork(UNIQUE_PERIODIC_NAME).get()
                .any { !it.state.isFinished }
        } catch (t: Throwable) {
            false
        }
        if (!alive) return
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork(interval),
        )
    }

    private fun periodicWork(interval: Duration): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<ArrPollWorker>(interval)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

    internal fun emitNotification(event: ArrEvent.NotificationIssued) {
        notificationBus.tryEmit(event)
    }

    private const val STATE_FILE_NAME = "arr_sync.preferences.pb"

    /** Reflective target of [ensureDefaultAdapters]; kept stable by `@Keep`. */
    private const val APP_BOOTSTRAP_CLASS = "dev.appolon.homelab.arr.ArrBootstrap"
}

/**
 * Persists scheduler bookkeeping across process restarts so quiet-day
 * escalation survives WorkManager spacing runs far apart. Public so tests
 * and the app integration can supply an in-memory fake.
 */
interface SyncStateStore {
    suspend fun lastIntervalMillis(): Long?
    suspend fun setLastIntervalMillis(value: Long)
}

class DataStoreSyncStateStore(
    private val dataStore: DataStore<Preferences>,
) : SyncStateStore {
    override suspend fun lastIntervalMillis(): Long? =
        dataStore.data.first()[longPreferencesKey(KEY_LAST_INTERVAL)]

    override suspend fun setLastIntervalMillis(value: Long) {
        dataStore.edit { it[longPreferencesKey(KEY_LAST_INTERVAL)] = value }
    }

    private companion object {
        const val KEY_LAST_INTERVAL = "arr_sync_v1_last_interval"
    }
}
