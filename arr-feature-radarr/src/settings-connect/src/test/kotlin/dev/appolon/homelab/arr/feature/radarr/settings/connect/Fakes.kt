package dev.appolon.homelab.arr.feature.radarr.settings.connect

import dev.appolon.homelab.arr.api.radarr.CommandApi
import dev.appolon.homelab.arr.api.radarr.TagApi
import dev.appolon.homelab.arr.api.radarr.MetadataApi
import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
        appType = AppType.RADARR,
        method = "PUT",
        path = path,
        description = "test action",
        enqueuedAtMillis = pendingFlow.value.size.toLong(),
    )
}

/** Minimal mutable [TagApi] for repository tests. */
class FakeConnectTagApi : TagApi {
    val tags = mutableListOf<TagResource>()

    override suspend fun tags(): List<TagResource> = tags.toList()

    override suspend fun createTag(body: TagResource): Response<TagResource> {
        val created = body.copy(id = (tags.maxOfOrNull { it.id } ?: 0) + 1)
        tags += created
        return Response.success(201, created)
    }

    override suspend fun updateTag(id: String, body: TagResource): Response<TagResource> {
        val idInt = id.toIntOrNull() ?: return Response.error(404, EMPTY_BODY)
        val index = tags.indexOfFirst { it.id == idInt }
        if (index < 0) return Response.error(404, EMPTY_BODY)
        tags[index] = body.copy(id = idInt)
        return Response.success(202, tags[index])
    }

    override suspend fun removeTag(id: Int): Response<Unit> {
        tags.removeAll { it.id == id }
        return Response.success(204, Unit)
    }

    override suspend fun tag(id: Int): Response<TagResource> {
        val found = tags.firstOrNull { it.id == id } ?: return Response.error(404, EMPTY_BODY)
        return Response.success(found)
    }
}

/** Minimal mutable [MetadataApi] for repository tests. */
class FakeConnectMetadataApi : MetadataApi {
    val rows = mutableListOf<MetadataResource>()
    val schemas = mutableListOf<MetadataResource>()
    val tested = mutableListOf<MetadataResource>()
    private var nextId = 1

    override suspend fun metadata(): List<MetadataResource> = rows.toList()

    override suspend fun createMetadata(
        forceSave: Boolean?,
        body: MetadataResource,
    ): Response<MetadataResource> {
        val created = body.copy(id = nextId++)
        rows += created
        return Response.success(201, created)
    }

    override suspend fun updateMetadata(
        id: Int,
        forceSave: Boolean?,
        body: MetadataResource,
    ): Response<MetadataResource> {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return Response.error(404, EMPTY_BODY)
        rows[index] = body.copy(id = id)
        return Response.success(202, rows[index])
    }

    override suspend fun removeMetadata(id: Int): Response<Unit> {
        rows.removeAll { it.id == id }
        return Response.success(204, Unit)
    }

    override suspend fun metadataById(id: Int): Response<MetadataResource> {
        val found = rows.firstOrNull { it.id == id } ?: return Response.error(404, EMPTY_BODY)
        return Response.success(found)
    }

    override suspend fun metadataSchema(): List<MetadataResource> = schemas.toList()

    override suspend fun testMetadata(forceSave: Boolean?, body: MetadataResource): Response<Unit> {
        tested += body
        return Response.success(204, Unit)
    }

    override suspend fun testAllMetadata(): Response<List<ProviderTestAllResult>> =
        Response.success(
            rows.map { ProviderTestAllResult(id = it.id, isValid = true, validationFailures = null) },
        )

    override suspend fun metadataAction(name: String, body: MetadataResource): Response<Unit> =
        Response.success(Unit)
}

/** Records the command bodies posted through [startCommand]. */
class FakeConnectCommandApi : CommandApi {
    val started = mutableListOf<CommandResource>()

    override suspend fun startCommand(body: CommandResource): Response<CommandResource> {
        started += body
        return Response.success(201, body)
    }

    override suspend fun commands(): List<CommandResource> = emptyList()

    override suspend fun cancelCommand(id: Int): Response<Unit> = Response.success(204, Unit)

    override suspend fun command(id: Int): Response<CommandResource> =
        Response.error(404, EMPTY_BODY)
}

private val EMPTY_BODY = "{}".toResponseBody()
