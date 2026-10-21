package dev.appolon.homelab.arr.feature.sonarr.settings.connect

import dev.appolon.homelab.arr.api.sonarr.CommandApi
import dev.appolon.homelab.arr.api.sonarr.MetadataApi
import dev.appolon.homelab.arr.api.sonarr.TagApi
import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** In-memory [ActionQueue] with the same oldest-first semantics as the real one. */
class FakeConnectActionQueue : ActionQueue {

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
        path: String = "api/v3/indexer",
    ): QueuedAction = QueuedAction(
        actionId = ActionId(id),
        serverId = serverId,
        appType = AppType.SONARR,
        method = "PUT",
        path = path,
        description = "test action",
        enqueuedAtMillis = pendingFlow.value.size.toLong(),
    )
}

/** Minimal mutable [TagApi] matching the Sonarr wire contract. */
class FakeConnectTagApi : TagApi {
    val tags = mutableListOf<TagResource>()

    override suspend fun list(): List<TagResource> = tags.toList()

    override suspend fun create(body: TagResource): TagResource {
        val created = body.copy(id = (tags.maxOfOrNull { it.id } ?: 0) + 1)
        tags += created
        return created
    }

    override suspend fun update(id: String, body: TagResource): TagResource {
        val idInt = id.toIntOrNull() ?: error("bad id")
        val index = tags.indexOfFirst { it.id == idInt }
        if (index < 0) error("missing tag")
        tags[index] = body.copy(id = idInt)
        return tags[index]
    }

    override suspend fun delete(id: Int): Unit {
        tags.removeAll { it.id == id }
    }

    override suspend fun get(id: Int): Response<TagResource> {
        val found = tags.firstOrNull { it.id == id }
            ?: return Response.error(404, "{}".toResponseBody())
        return Response.success(found)
    }
}

/** Records the command bodies posted through [create]. */
class FakeConnectCommandApi : CommandApi {
    val started = mutableListOf<CommandResource>()

    override suspend fun create(body: CommandResource): CommandResource {
        started += body
        return body
    }

    override suspend fun list(): List<CommandResource> = emptyList()

    override suspend fun delete(id: Int): Unit = Unit

    override suspend fun get(id: Int): Response<CommandResource> =
        Response.error(404, "{}".toResponseBody())
}

/** Minimal mutable [MetadataApi] matching the Sonarr wire contract. */
class FakeConnectMetadataApi : MetadataApi {
    val rows = mutableListOf<MetadataResource>()
    val schemas = mutableListOf<MetadataResource>()
    val tested = mutableListOf<MetadataResource>()
    val testedAllCount = mutableListOf<Int>()
    private var nextId = 1

    override suspend fun list(): List<MetadataResource> = rows.toList()

    override suspend fun create(
        forceSave: Boolean?,
        body: MetadataResource,
    ): MetadataResource {
        val created = body.copy(id = nextId++)
        rows += created
        return created
    }

    override suspend fun update(
        id: Int,
        forceSave: Boolean?,
        body: MetadataResource,
    ): MetadataResource {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) error("missing metadata consumer")
        rows[index] = body.copy(id = id)
        return rows[index]
    }

    override suspend fun delete(id: Int): Unit {
        rows.removeAll { it.id == id }
    }

    override suspend fun get(id: Int): Response<MetadataResource> {
        val found = rows.firstOrNull { it.id == id }
            ?: return Response.error(404, "{}".toResponseBody())
        return Response.success(found)
    }

    override suspend fun schema(): List<MetadataResource> = schemas.toList()

    override suspend fun test(forceTest: Boolean?, body: MetadataResource): Unit {
        tested += body
    }

    override suspend fun testAll(): Unit {
        testedAllCount.add(rows.size)
    }

    override suspend fun action(name: String, body: MetadataResource): Unit = Unit
}
