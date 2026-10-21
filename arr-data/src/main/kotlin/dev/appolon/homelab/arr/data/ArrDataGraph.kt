package dev.appolon.homelab.arr.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.registry.DataStorePreferencesStore
import dev.appolon.homelab.arr.data.registry.DataStoreServerRegistry
import dev.appolon.homelab.arr.data.registry.GlobalSettings
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.data.secrets.KeystoreArrSecrets
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.store.PlatformArrDatabase
import dev.appolon.homelab.arr.data.store.SqliteEntityStore
import dev.appolon.homelab.arr.data.store.StorePaths
import java.io.File

/**
 * Owns and wires the singletons of :arr-data. No DI framework: every
 * dependency is a lazy val and nothing touches disk, Keystore, or DataStore
 * until first access. Keep exactly one instance per process; DataStore allows
 * a single writer per file.
 */
class ArrDataGraph(
    context: Context,
    baseDir: File = context.getDir("arr", Context.MODE_PRIVATE),
) {
    val paths: StorePaths = StorePaths(baseDir)

    private val preferencesStore by lazy {
        DataStorePreferencesStore(
            PreferenceDataStoreFactory.create(produceFile = { paths.preferencesFile }),
        )
    }

    val secrets: ArrSecrets by lazy { KeystoreArrSecrets(paths.secretsDirectory) }

    val registry: DataStoreServerRegistry by lazy {
        DataStoreServerRegistry(preferencesStore, secrets)
    }

    val settings: GlobalSettings by lazy {
        // Secrets wired so the global proxy password round-trips encrypted,
        // matching the per-server registry behavior.
        GlobalSettings(preferencesStore, secrets)
    }

    private val databaseDelegate = lazy { PlatformArrDatabase.open(paths.database) }

    val entityStore: SqliteEntityStore by lazy { SqliteEntityStore(databaseDelegate.value) }

    val actionQueue: ActionQueueImpl by lazy { ActionQueueImpl(entityStore) }

    /** One-time startup work: stamps the settings schema version. */
    suspend fun initialize() {
        settings.ensureSchemaVersion()
    }

    /** Deletes a server everywhere it is referenced: registry, settings, cached entities. */
    suspend fun deleteServer(id: ServerId) {
        registry.delete(id)
        settings.clearServer(id)
        entityStore.deleteAll(id)
    }

    /** Releases the SQLite handle without forcing the lazy chain to run. */
    fun close() {
        if (databaseDelegate.isInitialized()) {
            databaseDelegate.value.close()
        }
    }
}
