package dev.appolon.homelab.arr.feature.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSystem
import dev.appolon.homelab.arr.api.prowlarr.HealthApi
import dev.appolon.homelab.arr.api.prowlarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.sync.scheduler.NotificationKind
import dev.appolon.homelab.arr.sync.scheduler.PollOutcome
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [ProwlarrPollAdapter] end to end against the committed
 * api-module fakes. Prowlarr has no queue, calendar, or disk endpoints in
 * its committed surface, so the notifiable signals are health findings and
 * indexers slipping into cooldown.
 */
class ProwlarrPollAdapterTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val server = ArrServer(
        id = ServerId("prowlarr-1"),
        type = AppType.PROWLARR,
        name = "Prowlarr",
        baseUrl = "http://prowlarr.example/",
        apiKeyEncrypted = "key",
    )

    private val system = FakeProwlarrSystem()
    private val indexers = FakeProwlarrIndexers()
    private val store = MemoryEntityStore()

    private fun adapter(health: HealthApi = system) = ProwlarrPollAdapter(
        secrets = PlainSecrets,
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        apis = { ProwlarrPollApis(health, indexers) },
    )

    @Test
    fun `first poll baselines without notifying`() = runTest {
        system.health += HealthResource(id = 1, type = HealthCheckResult.ERROR, message = "old")
        indexers.indexerStatuses += IndexerStatusResource(
            indexerId = 7,
            mostRecentFailure = "2026-10-20T11:00:00Z",
            disabledTill = "2026-10-20T18:00:00Z",
        )

        val result = adapter().poll(server)

        assertEquals(PollOutcome.QUIET, result.outcome())
        assertEquals(0, result.healthIssues)
        assertEquals(0, result.failures)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `indexer slipping into cooldown reports a failure once`() = runTest {
        adapter().poll(server)
        indexers.indexerStatuses += IndexerStatusResource(
            indexerId = 7,
            mostRecentFailure = "2026-10-20T11:00:00Z",
            disabledTill = "2026-10-20T18:00:00Z",
        )

        val result = adapter().poll(server)

        assertEquals(1, result.failures)
        val fail = result.events.single()
        assertEquals(NotificationKind.FAIL, fail.kind)
        assertEquals("indexer:7", fail.dedupeKey)

        val repeat = adapter().poll(server)
        assertEquals(0, repeat.failures)
        assertTrue(repeat.events.isEmpty())
    }

    @Test
    fun `recovered indexer with expired cooldown is not failing`() = runTest {
        adapter().poll(server)
        indexers.indexerStatuses += IndexerStatusResource(
            indexerId = 7,
            mostRecentFailure = "2026-10-20T08:00:00Z",
            disabledTill = "2026-10-20T10:00:00Z",
        )

        val result = adapter().poll(server)

        assertEquals(0, result.failures)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `new health issue notifies`() = runTest {
        adapter().poll(server)
        system.health += HealthResource(id = 4, source = "indexer", type = HealthCheckResult.WARNING, message = "Indexer unavailable")

        val result = adapter().poll(server)

        assertEquals(1, result.healthIssues)
        assertEquals(NotificationKind.HEALTH, result.events.single().kind)
    }

    @Test
    fun `health endpoint failing degrades the poll without losing indexer signals`() = runTest {
        adapter().poll(server)
        indexers.indexerStatuses += IndexerStatusResource(
            indexerId = 7,
            mostRecentFailure = "2026-10-20T11:00:00Z",
            disabledTill = "2026-10-20T18:00:00Z",
        )

        val result = adapter(health = FailingHealth).poll(server)

        assertEquals(1, result.failures)
        assertEquals(0, result.healthIssues)
        assertTrue(result.error is ArrError.Network)
    }
}

private object FailingHealth : HealthApi {
    override suspend fun getHealth(): Nothing = throw IOException("health down")
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
