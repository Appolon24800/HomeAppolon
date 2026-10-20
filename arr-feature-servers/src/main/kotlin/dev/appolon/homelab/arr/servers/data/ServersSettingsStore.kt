package dev.appolon.homelab.arr.servers.data

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Module-level preferences persisted outside GlobalSettings' fixed keys. */
@Serializable
data class ServersPreferences(
    val notificationsEnabled: Boolean = true,
)

/**
 * The global notification kill switch, stored through the committed
 * [EntityStore] under a synthetic scope, the same approach the action queue
 * uses, so this module adds no storage mechanism of its own. One JSON row
 * under a fixed id; absent means enabled.
 */
class ServersSettingsStore(
    private val store: EntityStore,
) {
    suspend fun notificationsEnabled(): Boolean = read()?.notificationsEnabled ?: true

    fun observeNotificationsEnabled(): Flow<Boolean> =
        store.observe(SCOPE, TYPE, ServersPreferences.serializer())
            .map { rows -> rows.firstOrNull()?.notificationsEnabled ?: true }
            .distinctUntilChanged()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.put(SCOPE, TYPE, ID, ServersPreferences(enabled), ServersPreferences.serializer())
    }

    private suspend fun read(): ServersPreferences? =
        store.get(SCOPE, TYPE, ID, ServersPreferences.serializer())

    companion object {
        internal val SCOPE = ServerId("__arr_servers_settings__")
        private val TYPE = EntityType("servers.settings")
        private const val ID = "global"
    }
}
