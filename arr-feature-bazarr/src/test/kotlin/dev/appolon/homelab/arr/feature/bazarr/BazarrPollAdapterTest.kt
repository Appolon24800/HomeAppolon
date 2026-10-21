package dev.appolon.homelab.arr.feature.bazarr

import dev.appolon.homelab.arr.api.bazarr.BadgesApi
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.sync.scheduler.NotificationKind
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [BazarrPollAdapter] end to end against the committed api-module
 * fakes: raw badges and health payloads in, scheduler counts and events out,
 * plus snapshot round-trip and per-endpoint degradation.
 */
class BazarrPollAdapterTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val server = ArrServer(
        id = ServerId("bazarr-1"),
        type = AppType.BAZARR,
        name = "Bazarr",
        baseUrl = "http://bazarr.example/",
        apiKeyEncrypted = "key",
    )

    private val system = FakeBazarrSystem()
    private val library = FakeBazarrLibrary()
    private val store = MemoryEntityStore()

    private fun adapter(badges: BadgesApi = library) = BazarrPollAdapter(
        secrets = PlainSecrets,
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        apis = { BazarrPollApis(system, badges) },
    )

    private fun badges(episodesMissing: Int, moviesMissing: Int) {
        library.responses["get_badges"] = buildJsonObject {
            put("episodes", buildJsonObject {
                put("missing", episodesMissing)
                put("wanted", episodesMissing)
            })
            put("movies", buildJsonObject {
                put("missing", moviesMissing)
                put("wanted", moviesMissing)
            })
        }
    }

    private fun healthJson(block: JsonObjectBuilder.() -> Unit) {
        system.responses["get_system_health"] = buildJsonObject(block)
    }

    @Test
    fun `first poll baselines without notifying`() = runTest {
        badges(episodesMissing = 5, moviesMissing = 2)
        healthJson {
            put("issue", buildJsonObject {
                put("message", "Provider unreachable")
                put("type", "error")
            })
        }

        val result = adapter().poll(server)

        assertEquals(0, result.newImports)
        assertEquals(0, result.failures)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `falling missing count reports subtitles found`() = runTest {
        badges(episodesMissing = 5, moviesMissing = 2)
        adapter().poll(server)
        badges(episodesMissing = 3, moviesMissing = 2)

        val result = adapter().poll(server)

        assertEquals(2, result.newImports)
        assertEquals(NotificationKind.IMPORT, result.events.single().kind)
    }

    @Test
    fun `rising missing count reports nothing`() = runTest {
        badges(episodesMissing = 5, moviesMissing = 2)
        adapter().poll(server)
        badges(episodesMissing = 9, moviesMissing = 4)

        val result = adapter().poll(server)

        assertEquals(0, result.newImports)
        assertTrue(result.events.none { it.kind == NotificationKind.IMPORT })
    }

    @Test
    fun `new health findings classify warnings and errors`() = runTest {
        badges(episodesMissing = 0, moviesMissing = 0)
        adapter().poll(server)
        healthJson {
            put("warning", buildJsonObject {
                put("message", "Disk usage high")
                put("type", "warning")
            })
            put("errors", buildJsonArray {
                add(
                    buildJsonObject {
                        put("message", "Provider login failed")
                        put("type", "error")
                    },
                )
            })
        }

        val result = adapter().poll(server)

        assertEquals(1, result.failures)
        assertEquals(2, result.healthIssues)
        assertEquals(
            setOf(NotificationKind.FAIL, NotificationKind.HEALTH),
            result.events.map { it.kind }.toSet(),
        )

        val repeat = adapter().poll(server)
        assertTrue(repeat.events.isEmpty())
    }

    @Test
    fun `badges endpoint failing degrades without faking progress`() = runTest {
        badges(episodesMissing = 5, moviesMissing = 2)
        adapter().poll(server)
        healthJson {
            put("issue", buildJsonObject {
                put("message", "New finding")
                put("type", "warning")
            })
        }

        val result = adapter(badges = FailingBadges).poll(server)

        assertTrue(result.error is ArrError.Network)
        assertTrue(result.events.none { it.kind == NotificationKind.IMPORT })
    }
}

private object FailingBadges : BadgesApi {
    override suspend fun getBadges(): Nothing = throw IOException("badges down")
}

private object PlainSecrets : ArrSecrets {
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(envelope: String): String = envelope
}

/** Minimal in-memory EntityStore; only what the adapter touches. */
private class MemoryEntityStore : EntityStore {
    val rows = mutableMapOf<String, String>()

    private fun key(serverId: ServerId, type: EntityType, id: String) =
        "${serverId.value}|${type.value}|$id"

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        rows[key(serverId, type, id)] = ArrJson.encodeToString(serializer, value)
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        values.forEach { (id, value) -> put(serverId, type, id, value, serializer) }
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> = rows.entries
        .filter { it.key.startsWith("${serverId.value}|${type.value}|") }
        .map { ArrJson.decodeFromString(serializer, it.value) }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? = rows[key(serverId, type, id)]?.let { ArrJson.decodeFromString(serializer, it) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.remove(key(serverId, type, id))
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        val prefix = type?.let { "${serverId.value}|${it.value}|" } ?: "${serverId.value}|"
        rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? = null

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = flow { emit(getAll(serverId, type, serializer)) }
}
