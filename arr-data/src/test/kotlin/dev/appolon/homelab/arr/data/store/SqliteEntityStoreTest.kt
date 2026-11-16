package dev.appolon.homelab.arr.data.store

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SqliteEntityStoreTest {

    @Serializable
    private data class Movie(val id: Int, val title: String, val year: Int? = null)

    private val server = ServerId("server-1")
    private val type = EntityType("radarr.movie")

    private fun store(
        db: InMemoryArrDatabase = InMemoryArrDatabase(),
    ): Pair<InMemoryArrDatabase, SqliteEntityStore> =
        Pair(db, SqliteEntityStore(db, ioDispatcher = Dispatchers.Unconfined))

    private fun movie(id: Int, title: String = "movie-$id", year: Int? = 2000) =
        Movie(id, title, year)

    @Test
    fun replacementRemovesDeletedRowsAndKeepsEmptyRefreshTime() = runTest {
        val (_, store) = store()
        val otherServer = ServerId("other")
        val otherType = EntityType("other")
        store.put(server, type, "1", movie(1), Movie.serializer())
        store.put(otherServer, type, "1", movie(1), Movie.serializer())
        store.put(server, otherType, "1", movie(1), Movie.serializer())
        store.replaceAll(server, type, listOf("2" to movie(2)), Movie.serializer(), 100L)
        assertEquals(listOf(movie(2)), store.getAll(server, type, Movie.serializer()))
        assertEquals(100L, store.lastFetchedAt(server, type))
        store.replaceAll(server, type, emptyList(), Movie.serializer(), 200L)
        assertTrue(store.getAll(server, type, Movie.serializer()).isEmpty())
        assertEquals(200L, store.lastFetchedAt(server, type))
        assertEquals(listOf(movie(1)), store.getAll(otherServer, type, Movie.serializer()))
        assertEquals(listOf(movie(1)), store.getAll(server, otherType, Movie.serializer()))
        store.deleteAll(server, type)
        assertNull(store.lastFetchedAt(server, type))
        store.replaceAll(server, type, emptyList(), Movie.serializer(), 300L)
        store.deleteAll(server)
        assertNull(store.lastFetchedAt(server, type))
    }

    @Test
    fun failedReplacementPreservesRowsAndPreviousRefreshTime() = runTest {
        val db = InMemoryArrDatabase()
        var fail = false
        val failingDb = object : ArrDatabase by db {
            override fun execute(sql: String, args: List<Any?>) {
                if (fail && sql == EntitySql.UPSERT_REFRESH) throw java.io.IOException("Disk full")
                db.execute(sql, args)
            }
        }
        val store = SqliteEntityStore(failingDb, ioDispatcher = Dispatchers.Unconfined)
        store.replaceAll(server, type, listOf("1" to movie(1)), Movie.serializer(), 100L)
        fail = true
        try {
            store.replaceAll(server, type, listOf("2" to movie(2)), Movie.serializer(), 200L)
            throw AssertionError("The replacement must fail")
        } catch (_: java.io.IOException) {
            assertEquals(listOf(movie(1)), store.getAll(server, type, Movie.serializer()))
            assertEquals(100L, store.lastFetchedAt(server, type))
        }
    }

    @Test
    fun failedMoveRollsBackBothWrites() = runTest {
        val db = InMemoryArrDatabase()
        val failingDb = object : ArrDatabase by db {
            override fun execute(sql: String, args: List<Any?>) {
                if (sql == EntitySql.DELETE_ONE) throw java.io.IOException("Disk full during move")
                db.execute(sql, args)
            }
        }
        val store = SqliteEntityStore(failingDb, ioDispatcher = Dispatchers.Unconfined)
        val target = EntityType("failed")
        store.put(server, type, "42", movie(42), Movie.serializer())
        try {
            store.move(server, type, target, "42", movie(42, "updated"), Movie.serializer())
            throw AssertionError("The move must fail")
        } catch (_: java.io.IOException) {
            assertEquals(movie(42), store.get(server, type, "42", Movie.serializer()))
            assertNull(store.get(server, target, "42", Movie.serializer()))
        }
    }

    @Test
    fun putAndGetRoundTripThroughTheJsonColumn() = runTest {
        val (db, store) = store()

        store.put(server, type, "42", movie(42), Movie.serializer())

        assertEquals(movie(42), store.get(server, type, "42", Movie.serializer()))
        assertNull(store.get(server, type, "missing", Movie.serializer()))

        // The persisted row holds the encoded JSON under the composite key.
        val row = db.rows.getValue(Triple("server-1", "radarr.movie", "42"))
        assertEquals("""{"id":42,"title":"movie-42","year":2000}""", row[3])
        assertTrue(row[4] is Long)
    }

    @Test
    fun nullableFieldsSurviveTheRoundTripWithExplicitNullsOff() = runTest {
        val (_, store) = store()

        store.put(server, type, "7", movie(7, year = null), Movie.serializer())

        assertEquals(movie(7, year = null), store.get(server, type, "7", Movie.serializer()))
    }

    @Test
    fun getAllReturnsEntitiesSortedById() = runTest {
        val (_, store) = store()

        store.putAll(
            server,
            type,
            listOf("c" to movie(3), "a" to movie(1), "b" to movie(2)),
            Movie.serializer(),
        )

        assertEquals(listOf(1, 2, 3), store.getAll(server, type, Movie.serializer()).map { it.id })
    }

    @Test
    fun putAllStampsTheSharedFetchedAtAndOverwritesExistingRows() = runTest {
        val (db, store) = store()
        store.put(server, type, "1", movie(1), Movie.serializer())

        store.putAll(
            server,
            type,
            listOf("1" to movie(1, title = "renamed"), "2" to movie(2)),
            Movie.serializer(),
            fetchedAtMillis = 5_000,
        )

        val all = store.getAll(server, type, Movie.serializer())
        assertEquals(listOf("renamed", "movie-2"), all.map { it.title })
        assertEquals(5_000L, store.lastFetchedAt(server, type))
        assertEquals(5_000L, (db.rows.values.first { it[2] == "1" }[4]))
    }

    @Test
    fun lastFetchedAtIsNullWhenScopeIsEmpty() = runTest {
        val (_, store) = store()

        assertNull(store.lastFetchedAt(server, type))
    }

    @Test
    fun scopesAreIsolatedByServerAndType() = runTest {
        val (_, store) = store()
        val otherServer = ServerId("server-2")
        val otherType = EntityType("radarr.queue")

        store.put(server, type, "1", movie(1), Movie.serializer())
        store.put(otherServer, type, "1", movie(1), Movie.serializer())
        store.put(server, otherType, "1", movie(1), Movie.serializer())

        assertEquals(1, store.getAll(server, type, Movie.serializer()).size)
        assertEquals(1, store.getAll(otherServer, type, Movie.serializer()).size)
        assertEquals(1, store.getAll(server, otherType, Movie.serializer()).size)
    }

    @Test
    fun deleteOneAndDeleteAllHonorTheirScope() = runTest {
        val (_, store) = store()
        val otherType = EntityType("radarr.queue")
        store.putAll(server, type, listOf("1" to movie(1), "2" to movie(2)), Movie.serializer())
        store.put(server, otherType, "9", movie(9), Movie.serializer())

        store.delete(server, type, "1")
        assertEquals(listOf(2), store.getAll(server, type, Movie.serializer()).map { it.id })

        store.deleteAll(server, type)
        assertTrue(store.getAll(server, type, Movie.serializer()).isEmpty())
        assertEquals(listOf(9), store.getAll(server, otherType, Movie.serializer()).map { it.id })

        store.deleteAll(server)
        assertTrue(store.getAll(server, otherType, Movie.serializer()).isEmpty())
    }

    @Test
    fun observeEmitsInitiallyAndOnMeaningfulChangesOnly() = runTest {
        val (_, store) = store()
        val collected = mutableListOf<List<Movie>>()

        // The store runs on the unconfined dispatcher here, so reads and
        // invalidations complete without leaving the scheduler. Ticks are
        // still delivered through the scheduler queue: a burst of writes may
        // collapse into one re-read (dedup on value). The contract under
        // test is the distinct emission sequence ending at the final state.
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            store.observe(server, type, Movie.serializer()).toList(collected)
        }

        store.put(server, type, "1", movie(1), Movie.serializer())
        // Rewriting the identical value must not produce a new distinct list.
        store.put(server, type, "1", movie(1), Movie.serializer())
        store.put(server, type, "2", movie(2), Movie.serializer())
        advanceUntilIdle()
        collector.cancel()
        collector.join()

        val seen = collected.map { list -> list.map { it.id } }
        assertTrue(seen.first().isEmpty())
        assertEquals(listOf(1, 2), seen.last())
        assertEquals(seen, seen.distinct())
        assertTrue(seen.size <= 3)
    }

    @Test
    fun writesForOtherScopesDoNotDisturbObserveContent() = runTest {
        val (_, store) = store()
        val otherServer = ServerId("server-2")

        // A write to a different scope still emits (cheap re-read) but the
        // observed content for this scope stays the same; the dedup happens
        // on the value, so the collector sees no duplicate list.
        store.put(server, type, "1", movie(1), Movie.serializer())
        val first = store.observe(server, type, Movie.serializer()).take(1)

        assertEquals(listOf(1), first.toList().single().map { it.id })
        store.put(otherServer, type, "2", movie(2), Movie.serializer())
        assertEquals(listOf(1), store.getAll(server, type, Movie.serializer()).map { it.id })
    }
}
