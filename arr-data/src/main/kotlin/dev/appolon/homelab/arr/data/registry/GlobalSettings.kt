package dev.appolon.homelab.arr.data.registry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataJson
import dev.appolon.homelab.arr.net.ProxyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable

/**
 * Notification channel toggles for one server. The kinds match the event
 * categories :arr-sync emits (grab, import, fail, health, disk).
 */
@Serializable
data class NotificationChannels(
    val grab: Boolean = true,
    val import: Boolean = true,
    val fail: Boolean = true,
    val health: Boolean = true,
    val disk: Boolean = true,
)

/**
 * App-wide arr settings in preference storage under "arr_global_v1_" keys:
 * the default proxy applied when a server has none of its own, per-server
 * notification channel toggles, and the settings schema version.
 */
class GlobalSettings internal constructor(
    private val store: PreferencesStore,
) {
    constructor(dataStore: DataStore<Preferences>) : this(DataStorePreferencesStore(dataStore))

    /** The global proxy default; [ProxyConfig] with type NONE means "direct". */
    suspend fun defaultProxy(): ProxyConfig =
        store.string(KEY_DEFAULT_PROXY)
            ?.let { ArrDataJson.decodeFromString(ProxyConfig.serializer(), it) }
            ?: ProxyConfig()

    fun observeDefaultProxy(): Flow<ProxyConfig> =
        store.changes()
            .onStart { emit(Unit) }
            .map { defaultProxy() }
            .distinctUntilChanged()

    suspend fun setDefaultProxy(proxy: ProxyConfig) {
        store.putString(
            KEY_DEFAULT_PROXY,
            ArrDataJson.encodeToString(ProxyConfig.serializer(), proxy),
        )
    }

    suspend fun notificationChannels(serverId: ServerId): NotificationChannels =
        store.string(notificationKey(serverId))
            ?.let { ArrDataJson.decodeFromString(NotificationChannels.serializer(), it) }
            ?: NotificationChannels()

    fun observeNotificationChannels(serverId: ServerId): Flow<NotificationChannels> =
        store.changes()
            .onStart { emit(Unit) }
            .map { notificationChannels(serverId) }
            .distinctUntilChanged()

    suspend fun setNotificationChannels(serverId: ServerId, channels: NotificationChannels) {
        store.putString(
            notificationKey(serverId),
            ArrDataJson.encodeToString(NotificationChannels.serializer(), channels),
        )
    }

    /** Drops a server's toggles; call from server deletion flows. */
    suspend fun clearServer(serverId: ServerId) {
        store.remove(notificationKey(serverId))
    }

    /**
     * Stamps [SCHEMA_VERSION] when stored storage is older. Future key-shape
     * changes bump [SCHEMA_VERSION] and run their migration steps here.
     */
    suspend fun ensureSchemaVersion() {
        val stored = store.int(KEY_SCHEMA_VERSION) ?: 0
        if (stored < SCHEMA_VERSION) {
            store.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        }
    }

    suspend fun storedSchemaVersion(): Int = store.int(KEY_SCHEMA_VERSION) ?: 0

    private fun notificationKey(serverId: ServerId): String =
        KEY_NOTIFICATION_PREFIX + serverId.value

    companion object {
        /** Current settings schema. Bump only with a migration in [ensureSchemaVersion]. */
        const val SCHEMA_VERSION = 1

        private const val KEY_SCHEMA_VERSION = "arr_global_v1_schema_version"
        private const val KEY_DEFAULT_PROXY = "arr_global_v1_default_proxy"
        private const val KEY_NOTIFICATION_PREFIX = "arr_global_v1_notifications_"
    }
}
