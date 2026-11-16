package dev.appolon.homelab.arr.sync.scheduler

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStatePersistenceTest {
    @Test
    fun schedulerIntervalPersistsUsingTheProductionFileName() = runBlocking {
        val directory = Files.createTempDirectory("arr-sync-state").toFile()
        val file = directory.resolve(SyncBootstrap.STATE_FILE_NAME)
        suspend fun withStore(block: suspend (DataStoreSyncStateStore) -> Unit) {
            val job = SupervisorJob()
            try {
                val dataStore = PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(job + Dispatchers.IO),
                    produceFile = { file },
                )
                block(DataStoreSyncStateStore(dataStore))
            } finally {
                job.cancelAndJoin()
            }
        }
        try {
            withStore {
                assertNull(it.lastIntervalMillis())
                it.setLastIntervalMillis(900_000L)
            }
            withStore { assertEquals(900_000L, it.lastIntervalMillis()) }
        } finally {
            directory.deleteRecursively()
        }
    }
}
