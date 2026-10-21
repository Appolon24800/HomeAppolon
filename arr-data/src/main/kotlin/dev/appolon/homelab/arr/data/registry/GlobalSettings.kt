package dev.appolon.homelab.arr.data.registry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataJson
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
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
 *
 * The default proxy's password is encrypted with [secrets] before it is
 * written and decrypted on read. The secrets instance is optional only so
 * existing wiring keeps compiling; production should pass the data graph's
 * [ArrSecrets], and with it absent the password round-trips verbatim.
 */
class GlobalSettings internal constructor(
    private val store: PreferencesStore,
    private val secrets: ArrSecrets? = null,
) {
    constructor(dataStore: DataStore<Preferences>, secrets: ArrSecrets? = null) :
        this(DataStorePreferencesStore(dataStore), secrets)

    /** The global proxy default; [ProxyConfig] with type NONE means "direct". */
    suspend fun defaultProxy(): ProxyConfig =
        store.string(KEY_DEFAULT_PROXY)
            ?.let { ArrDataJson.decodeFromString(ProxyConfig.serializer(), it).withDecryptedPassword() }
            ?: ProxyConfig()

    fun observeDefaultProxy(): Flow<ProxyConfig> =
        store.changes()
            .onStart { emit(Unit) }
            .map { defaultProxy() }
            .distinctUntilChanged()

    suspend fun setDefaultProxy(proxy: ProxyConfig) {
        store.putString(
            KEY_DEFAULT_PROXY,
            ArrDataJson.encodeToString(ProxyConfig.serializer(), proxy.withEncryptedPassword()),
        )
    }

    /** Encrypts the proxy password before persistence; envelopes pass through. */
    private fun ProxyConfig.withEncryptedPassword(): ProxyConfig {
        val password = password ?: return this
        if (secrets == null || ArrSecrets.isEncrypted(password)) return this
        return copy(password = secrets.encrypt(password))
    }

    /**
     * Decrypts the stored password. An envelope that no longer opens (for
     * example after a Keystore reset) drops the password so the proxy fails
     * authentication instead of sending the envelope as a credential.
     */
    private fun ProxyConfig.withDecryptedPassword(): ProxyConfig {
        val password = password ?: return this
        if (secrets == null || !ArrSecrets.isEncrypted(password)) return this
        return copy(password = runCatching { secrets.decrypt(password) }.getOrNull())
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
