package dev.appolon.homelab.arr.feature.bazarr.series

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * In-memory [EntityStore] mirroring the SQLite contract closely enough for
 * repository and ViewModel tests: rows keyed by (server, type, id), observe
 * re-emits per type on every write, and putAll stamps the shared fetchedAt.
 */
class FakeEntityStore : EntityStore {

    private data class Row(
        val serverId: ServerId,
        val type: EntityType,
        val id: String,
        val json: String,
    )

    private val rows = MutableStateFlow<List<Row>>(emptyList())
    private val fetchedAt = HashMap<Pair<ServerId, EntityType>, Long>()

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        putAll(serverId, type, listOf(id to value), serializer)
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        val encoded = values.map { (id, value) ->
            Row(serverId, type, id, dev.appolon.homelab.arr.net.ArrJson.encodeToString(serializer, value))
        }
        rows.value = rows.value.filterNot { row ->
            row.serverId == serverId && row.type == type && row.id in values.map { it.first }
        } + encoded
        fetchedAt[serverId to type] = fetchedAtMillis
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> =
        rows.value.filter { it.serverId == serverId && it.type == type }
            .map { decode(it, serializer) }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? =
        rows.value.firstOrNull { it.serverId == serverId && it.type == type && it.id == id }
            ?.let { decode(it, serializer) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.value = rows.value.filterNot {
            it.serverId == serverId && it.type == type && it.id == id
        }
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        rows.value = rows.value.filterNot {
            it.serverId == serverId && (type == null || it.type == type)
        }
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        fetchedAt[serverId to type]

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = rows.map { snapshot ->
        snapshot.filter { it.serverId == serverId && it.type == type }
            .map { decode(it, serializer) }
    }

    private fun <T> decode(row: Row, serializer: KSerializer<T>): T =
        dev.appolon.homelab.arr.net.ArrJson.decodeFromString(serializer, row.json)
}

/** In-memory [ActionQueue] with the same oldest-first semantics as the real one. */
class FakeActionQueue : ActionQueue {

    val pendingFlow = MutableStateFlow<List<QueuedAction>>(emptyList())

    override suspend fun enqueue(action: QueuedAction) {
        pendingFlow.value = (pendingFlow.value + action).sortedBy { it.enqueuedAtMillis }
    }

    override suspend fun pending(): List<QueuedAction> = pendingFlow.value

    override fun observe(): Flow<List<QueuedAction>> = pendingFlow

    override suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int> {
        var succeeded = 0
        var failed = 0
        pendingFlow.value.toList().forEach { action ->
            if (executor(action)) {
                pendingFlow.value = pendingFlow.value - action
                succeeded++
            } else {
                failed++
            }
        }
        return succeeded to failed
    }

    override suspend fun remove(actionId: ActionId) {
        pendingFlow.value = pendingFlow.value.filterNot { it.actionId == actionId }
    }

    fun action(
        id: String,
        serverId: ServerId = ServerId("s1"),
        path: String = "series?seriesid=1&profileid=2",
    ): QueuedAction = QueuedAction(
        actionId = ActionId(id),
        serverId = serverId,
        appType = AppType.BAZARR,
        method = "POST",
        path = path,
        description = "test action",
        enqueuedAtMillis = pendingFlow.value.size.toLong(),
    )
}

/** Spec-shaped JSON builders for seeding [dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary]. */
object BazarrTestJson {

    fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    fun array(vararg elements: JsonElement): JsonArray = JsonArray(elements.toList())
    fun language(code2: String, name: String = code2, hi: Boolean = false): JsonObject = obj(
        "code2" to JsonPrimitive(code2),
        "name" to JsonPrimitive(name),
        "hi" to JsonPrimitive(hi),
        "forced" to JsonPrimitive(false),
    )

    fun languageArray(codes: List<String>): JsonArray =
        JsonArray(codes.map { language(it) })

    fun seriesItem(
        id: Int,
        title: String,
        profileId: Int? = 1,
        fileCount: Int = 10,
        missingCount: Int = 0,
        tags: List<String> = emptyList(),
        audio: List<String> = listOf("eng"),
        year: String = "2020",
    ): JsonObject = JsonObject(
        buildMap {
            put("sonarrSeriesId", JsonPrimitive(id))
            put("title", JsonPrimitive(title))
            put("year", JsonPrimitive(year))
            put("monitored", JsonPrimitive(true))
            put("poster", JsonPrimitive("https://img/$id.jpg"))
            put("audio_language", languageArray(audio))
            put("tags", JsonArray(tags.map(::JsonPrimitive)))
            put("episodeFileCount", JsonPrimitive(fileCount))
            put("episodeMissingCount", JsonPrimitive(missingCount))
            profileId?.let { put("profileId", JsonPrimitive(it)) }
        },
    )

    fun movieItem(
        id: Int,
        title: String,
        profileId: Int? = 1,
        subtitles: List<JsonObject> = emptyList(),
        missing: List<JsonObject> = emptyList(),
        tags: List<String> = emptyList(),
        audio: List<String> = listOf("eng"),
        year: String = "2021",
    ): JsonObject = JsonObject(
        buildMap {
            put("radarrId", JsonPrimitive(id))
            put("title", JsonPrimitive(title))
            put("year", JsonPrimitive(year))
            put("monitored", JsonPrimitive(true))
            put("audio_language", languageArray(audio))
            put("subtitles", JsonArray(subtitles))
            put("missing_subtitles", JsonArray(missing))
            put("tags", JsonArray(tags.map(::JsonPrimitive)))
            profileId?.let { put("profileId", JsonPrimitive(it)) }
        },
    )

    fun episodeItem(
        seriesId: Int,
        episodeId: Int,
        season: Int,
        episode: Int,
        title: String,
        subtitles: List<JsonObject> = emptyList(),
        missing: List<JsonObject> = emptyList(),
    ): JsonObject = JsonObject(
        buildMap {
            put("sonarrSeriesId", JsonPrimitive(seriesId))
            put("sonarrEpisodeId", JsonPrimitive(episodeId))
            put("season", JsonPrimitive(season))
            put("episode", JsonPrimitive(episode))
            put("title", JsonPrimitive(title))
            put("monitored", JsonPrimitive(true))
            put("subtitles", JsonArray(subtitles))
            put("missing_subtitles", JsonArray(missing))
        },
    )

    fun profileItem(
        profileId: Int,
        name: String,
        languages: List<String>,
        cutoff: Int? = null,
    ): JsonObject = JsonObject(
        buildMap {
            put("profileId", JsonPrimitive(profileId))
            put("name", JsonPrimitive(name))
            cutoff?.let { put("cutoff", JsonPrimitive(it)) }
            put(
                "items",
                JsonArray(
                    languages.map { code -> obj("language" to JsonPrimitive(code)) },
                ),
            )
        },
    )

    fun blacklistItem(
        provider: String,
        seriesId: Int? = null,
        radarrId: Int? = null,
        title: String = "Entry",
    ): JsonObject = JsonObject(
        buildMap {
            put("provider", JsonPrimitive(provider))
            seriesId?.let { put("sonarrSeriesId", JsonPrimitive(it)) }
            radarrId?.let { put("radarrId", JsonPrimitive(it)) }
            put("seriesTitle", JsonPrimitive(title))
            put("parsed_timestamp", JsonPrimitive("01/01/26 00:00:00"))
        },
    )
}
