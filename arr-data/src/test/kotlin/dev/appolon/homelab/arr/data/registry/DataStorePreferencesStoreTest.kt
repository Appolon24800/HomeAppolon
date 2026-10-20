package dev.appolon.homelab.arr.data.registry

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the DataStore-backed adapter against a real Preferences file so the
 * production binding (not just the fake) is covered on the JVM. DataStore
 * allows one instance per file per process, so every test owns a fresh
 * directory.
 */
class DataStorePreferencesStoreTest {

    @Test
    fun writesPersistToDiskAndReadBack() = runTest {
        val directory = tempDirectory()
        val store = newStore(directory)

        store.putString("arr_global_v1_default_proxy", "proxy-json")
        store.putInt("arr_global_v1_schema_version", 1)

        assertEquals("proxy-json", store.string("arr_global_v1_default_proxy"))
        assertEquals(1, store.int("arr_global_v1_schema_version"))
        assertTrue(
            store.keys().containsAll(
                setOf("arr_global_v1_default_proxy", "arr_global_v1_schema_version"),
            ),
        )

        // The key names must live in the file, not just the in-memory snapshot.
        val fileBytes = File(directory, "test.preferences_pb").readBytes()
        assertTrue(String(fileBytes, Charsets.UTF_8).contains("arr_global_v1_default_proxy"))

        store.remove("arr_global_v1_default_proxy")
        assertNull(store.string("arr_global_v1_default_proxy"))
        assertFalse("arr_global_v1_default_proxy" in store.keys())
    }

    @Test
    fun changesEmitsAfterEachWrite() = runTest {
        val store = newStore(tempDirectory())
        val ticks = mutableListOf<Unit>()

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.changes().take(2).toList(ticks)
        }

        store.putString("k1", "v1")
        store.putInt("k2", 2)
        runCurrent()
        collector.join()

        assertEquals(2, ticks.size)
    }

    private fun tempDirectory(): File = Files.createTempDirectory("arr-data-test").toFile()

    /** Runs DataStore on a scope cancelled when the test ends. */
    private fun TestScope.newStore(directory: File): DataStorePreferencesStore {
        val scope = CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
        )
        return DataStorePreferencesStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(directory, "test.preferences_pb") },
            ),
        )
    }
}
