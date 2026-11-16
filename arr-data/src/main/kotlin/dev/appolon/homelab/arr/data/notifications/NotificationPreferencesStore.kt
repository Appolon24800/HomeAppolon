package dev.appolon.homelab.arr.data.notifications

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class NotificationPreferences(val notificationsEnabled: Boolean = true)

/** Shares the persisted notification switch between settings and background polling. */
class NotificationPreferencesStore(private val store: EntityStore) {
    suspend fun notificationsEnabled(): Boolean =
        store.get(SCOPE, TYPE, ID, NotificationPreferences.serializer())
            ?.notificationsEnabled ?: true

    fun observeNotificationsEnabled(): Flow<Boolean> =
        store.observe(SCOPE, TYPE, NotificationPreferences.serializer())
            .map { rows -> rows.firstOrNull()?.notificationsEnabled ?: true }
            .distinctUntilChanged()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.put(SCOPE, TYPE, ID, NotificationPreferences(enabled), NotificationPreferences.serializer())
    }

    private companion object {
        // Keep the original settings screen's keys so existing choices survive updates.
        val SCOPE = ServerId("__arr_servers_settings__")
        val TYPE = EntityType("servers.settings")
        const val ID = "global"
    }
}
