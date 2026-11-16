package dev.appolon.homelab

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataGraph
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntityStoreMigrationTest {
    @Test
    fun versionOneCacheSurvivesUpgradeAndEmptyRefreshSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "entity-migration-${System.nanoTime()}")
        directory.mkdirs()
        val graph = ArrDataGraph(context, directory)
        val server = ServerId("migration-test")
        val type = EntityType("profiles")
        val file = graph.paths.database.file()
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE entities (serverId TEXT NOT NULL, type TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY (serverId, type, id))")
            db.execSQL("INSERT INTO entities VALUES (?, ?, ?, ?, ?)", arrayOf<Any>(server.value, type.value, "1", "\"original\"", 100L))
            db.version = 1
        }
        var graphClosed = false
        try {
            val store = graph.entityStore
            assertEquals(listOf("original"), store.getAll(server, type, String.serializer()))
            assertEquals(100L, store.lastFetchedAt(server, type))
            store.replaceAll(server, type, listOf("2" to "replacement"), String.serializer(), 200L)
            assertEquals(listOf("replacement"), store.getAll(server, type, String.serializer()))
            store.replaceAll(server, type, emptyList(), String.serializer(), 300L)
            graph.close()
            graphClosed = true
            val reopened = ArrDataGraph(context, directory)
            try {
                assertTrue(reopened.entityStore.getAll(server, type, String.serializer()).isEmpty())
                assertEquals(300L, reopened.entityStore.lastFetchedAt(server, type))
                reopened.entityStore.deleteAll(server)
                assertNull(reopened.entityStore.lastFetchedAt(server, type))
            } finally {
                reopened.close()
            }
        } finally {
            if (!graphClosed) graph.close()
            directory.deleteRecursively()
        }
    }
}
