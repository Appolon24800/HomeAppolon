package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ApiInfoResource
import dev.appolon.homelab.arr.api.prowlarr.model.BackupResource
import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import dev.appolon.homelab.arr.api.prowlarr.model.LogFileResource
import dev.appolon.homelab.arr.api.prowlarr.model.LogResource
import dev.appolon.homelab.arr.api.prowlarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.PingResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import dev.appolon.homelab.arr.api.prowlarr.model.UpdateResource
import kotlin.comparisons.nullsLast
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** One recorded fake invocation with its wire-shaped arguments. */
data class ProwlarrSystemCall(val operation: String, val arguments: Map<String, Any?>)

/**
 * In-memory implementation of every system-group interface for UI tests.
 * Seed the state, then drive screens through any interface.
 *
 * [latency] is suspending so tests inject delay without blocking; [failure]
 * throws before any state changes (transport or cancellation failures); and
 * [errorStatus] turns one call into an HTTP 4xx or 5xx, thrown as
 * [HttpException] for plain calls and returned inside [Response] for calls
 * whose signatures carry the status. [calls] records every invocation for
 * assertion. Reads of unknown ids and restore of unknown backups throw the
 * matching 404, mirroring the server.
 *
 * Log listing filters on `level` (case-insensitive equality), sorts on the
 * `time` key (other keys pass through in seed order) and pages with the
 * spec's 1-based pages. Content and static routes return fresh
 * [ResponseBody]s seeded from string properties so each consume-and-close
 * behaves like the network. [shutdownRequested] and [restartRequested] flip
 * only when the matching call succeeds. Configure and use the fake from one
 * test dispatcher.
 */
class FakeProwlarrSystem(
    var latency: suspend (ProwlarrSystemCall) -> Unit = {},
    var errorStatus: (ProwlarrSystemCall) -> Int? = { null },
    var failure: (ProwlarrSystemCall) -> Throwable? = { null },
) : SystemApi,
    BackupApi,
    HealthApi,
    LogApi,
    LogFileApi,
    UpdateApi,
    UpdateLogFileApi,
    TaskApi,
    PingApi,
    ApiInfoApi,
    StaticResourceApi {

    val calls: MutableList<ProwlarrSystemCall> = mutableListOf()
    val backups = mutableListOf<BackupResource>()
    val health = mutableListOf<HealthResource>()
    val logs = mutableListOf<LogResource>()
    val logFiles = mutableListOf<LogFileResource>()
    val updateLogFiles = mutableListOf<LogFileResource>()
    val updates = mutableListOf<UpdateResource>()
    val tasks = mutableMapOf<Int, TaskResource>()

    var status: SystemResource = SystemResource()
    var apiInfo: ApiInfoResource = ApiInfoResource(current = "v1")
    var ping: PingResource = PingResource(status = "OK")
    var routesBody: String = "GET api/v1/system/status"
    var routesDuplicate: JsonObject = buildJsonObject { put("api/v1/system/status", "GET") }
    var logFileBody: String = "2026-10-20 08:00:00.0|Info|Fake|Spec log line"
    var staticBody: String = "<!DOCTYPE html><html><body>Prowlarr</body></html>"

    /** Flips when a shutdown call passes the injection gates. */
    var shutdownRequested = false
        private set

    /** Flips when a restart call passes the injection gates. */
    var restartRequested = false
        private set

    private suspend fun gate(operation: String, vararg arguments: Pair<String, Any?>): Int? {
        val call = ProwlarrSystemCall(operation, arguments.toMap())
        calls += call
        latency(call)
        failure(call)?.let { throw it }
        val status = errorStatus(call)
        require(status == null || status in 400..599) { "Injected status must be 4xx or 5xx" }
        return status
    }

    /** Gate for plain-returning calls: injected statuses throw, like Retrofit. */
    private suspend fun read(operation: String, vararg arguments: Pair<String, Any?>) {
        gate(operation, *arguments)?.let(::httpError)
    }

    /** Gate for Response-returning calls: injected statuses ride the Response. */
    private suspend fun respond(
        operation: String,
        vararg arguments: Pair<String, Any?>,
    ): Response<Unit> = gate(operation, *arguments)
        ?.let { Response.error(it, "{}".toResponseBody()) }
        ?: Response.success(200, Unit)

    private fun httpError(status: Int): Nothing =
        throw HttpException(Response.error<Unit>(status, "{}".toResponseBody()))

    private fun <T : Any> stored(map: Map<Int, T>, id: Int): T = map[id] ?: httpError(404)

    private fun body(text: String, mediaType: String = "text/plain"): ResponseBody =
        text.toByteArray().toResponseBody(mediaType.toMediaType())

    private suspend fun static(
        operation: String,
        vararg arguments: Pair<String, Any?>,
    ): Response<ResponseBody> {
        val status = gate(operation, *arguments)
        return status?.let { Response.error(it, body(staticBody, "text/html")) }
            ?: Response.success(200, body(staticBody, "text/html"))
    }

    override suspend fun getSystemStatus(): SystemResource {
        read("getSystemStatus")
        return status
    }

    override suspend fun getSystemRoutes(): ResponseBody {
        read("getSystemRoutes")
        return body(routesBody)
    }

    override suspend fun getSystemRoutesDuplicate(): JsonObject {
        read("getSystemRoutesDuplicate")
        return routesDuplicate
    }

    override suspend fun shutdown(): Response<Unit> =
        respond("shutdown").also { if (it.isSuccessful) shutdownRequested = true }

    override suspend fun restart(): Response<Unit> =
        respond("restart").also { if (it.isSuccessful) restartRequested = true }

    override suspend fun getBackups(): List<BackupResource> {
        read("getBackups")
        return backups.toList()
    }

    private fun requireBackup(id: Int): BackupResource =
        backups.firstOrNull { it.id == id } ?: httpError(404)

    override suspend fun deleteBackup(id: Int): Response<Unit> {
        val status = gate("deleteBackup", "id" to id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: run {
                requireBackup(id)
                backups.removeAll { it.id == id }
                Response.success(200, Unit)
            }
    }

    override suspend fun restoreBackup(id: Int): Response<Unit> {
        val status = gate("restoreBackup", "id" to id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: run {
                requireBackup(id)
                Response.success(200, Unit)
            }
    }

    override suspend fun restoreBackupUpload(): Response<Unit> =
        respond("restoreBackupUpload")

    override suspend fun getHealth(): List<HealthResource> {
        read("getHealth")
        return health.toList()
    }

    override suspend fun getLog(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        level: String?,
    ): LogResourcePagingResource {
        read(
            "getLog",
            "page" to page,
            "pageSize" to pageSize,
            "sortKey" to sortKey,
            "sortDirection" to sortDirection,
            "level" to level,
        )
        var matched = logs.toList()
        level?.let { wanted -> matched = matched.filter { it.level.equals(wanted, true) } }
        if (sortKey == "time") {
            val byTime = nullsLast(compareBy<LogResource> { it.time })
            matched = when (sortDirection) {
                SortDirection.DESCENDING -> matched.sortedWith(byTime.reversed())
                else -> matched.sortedWith(byTime)
            }
        }
        val requestedPage = (page ?: 1).coerceAtLeast(1)
        val requestedSize = (pageSize ?: 10).coerceAtLeast(0)
        val from = (requestedPage - 1) * requestedSize
        return LogResourcePagingResource(
            page = requestedPage,
            pageSize = requestedSize,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = matched.size,
            records = matched.drop(from).take(requestedSize),
        )
    }

    override suspend fun getLogFiles(): List<LogFileResource> {
        read("getLogFiles")
        return logFiles.toList()
    }

    override suspend fun getLogFileContents(filename: String): ResponseBody {
        read("getLogFileContents", "filename" to filename)
        return body(logFileBody)
    }

    override suspend fun getUpdates(): List<UpdateResource> {
        read("getUpdates")
        return updates.toList()
    }

    override suspend fun getUpdateLogFiles(): List<LogFileResource> {
        read("getUpdateLogFiles")
        return updateLogFiles.toList()
    }

    override suspend fun getUpdateLogFileContents(filename: String): ResponseBody {
        read("getUpdateLogFileContents", "filename" to filename)
        return body(logFileBody)
    }

    override suspend fun getTasks(): List<TaskResource> {
        read("getTasks")
        return tasks.values.toList()
    }

    override suspend fun getTask(id: Int): TaskResource {
        read("getTask", "id" to id)
        return stored(tasks, id)
    }

    override suspend fun ping(): PingResource {
        read("ping")
        return ping
    }

    override suspend fun pingHead(): Response<Unit> = respond("pingHead")

    override suspend fun getApiInfo(): ApiInfoResource {
        read("getApiInfo")
        return apiInfo
    }

    override suspend fun getLoginPage(): Response<ResponseBody> =
        static("getLoginPage")

    override suspend fun getContent(path: String): Response<ResponseBody> =
        static("getContent", "path" to path)

    override suspend fun getRoot(): Response<ResponseBody> = static("getRoot")

    override suspend fun getStaticResource(path: String): Response<ResponseBody> =
        static("getStaticResource", "path" to path)
}
