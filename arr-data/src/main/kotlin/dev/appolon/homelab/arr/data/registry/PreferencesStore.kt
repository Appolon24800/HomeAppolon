package dev.appolon.homelab.arr.data.registry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Minimal typed-key persistence seam. It exists so registry and settings
 * logic run in JVM tests against an in-memory fake instead of DataStore.
 * Production binds [DataStorePreferencesStore].
 */
internal interface PreferencesStore {
    suspend fun string(key: String): String?
    suspend fun int(key: String): Int?
    suspend fun putString(key: String, value: String)
    suspend fun putInt(key: String, value: Int)
    suspend fun remove(key: String)
    suspend fun keys(): Set<String>

    /** Emits after every successful write; must not replay write history. */
    fun changes(): Flow<Unit>
}

/** [PreferencesStore] over DataStore Preferences. */
internal class DataStorePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStore {

    override suspend fun string(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun int(key: String): Int? =
        dataStore.data.first()[intPreferencesKey(key)]

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun putInt(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { preferences ->
            // A name may have been written under either type; clear both.
            preferences.remove(stringPreferencesKey(key))
            preferences.remove(intPreferencesKey(key))
        }
    }

    override suspend fun keys(): Set<String> =
        dataStore.data.first().asMap().keys.map { it.name }.toSet()

    /** Each DataStore edit emits a new Preferences; drop the current snapshot. */
    override fun changes(): Flow<Unit> = dataStore.data.drop(1).map { Unit }
}
