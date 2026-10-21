package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** A captured request retains every argument without lossy conversion. */
data class BazarrSystemCall(val operation: String, val arguments: Map<String, Any?>)

/**
 * In-memory scripted system endpoints for UI tests. Seed [responses] keyed by operation id with
 * raw spec-compatible objects. [latency] is suspending so callers can delay on a test dispatcher.
 * [errorStatus] injects HTTP errors, while [failure] injects transport or cancellation failures.
 * [mutate] runs after successful writes and can update [responses] for the next read.
 * Configure and use this fake from one test dispatcher.
 */
class FakeBazarrSystem(
    var latency: suspend (BazarrSystemCall) -> Unit = {},
    var errorStatus: (BazarrSystemCall) -> Int? = { null },
    var failure: (BazarrSystemCall) -> Throwable? = { null },
    var mutate: (BazarrSystemCall, MutableMap<String, JsonObject>) -> Unit = { _, _ -> },
) : SystemAnnouncementsApi, SystemBackupsApi, SystemHealthApi, SystemJobsApi,
    SystemLanguagesApi, SystemLanguagesProfilesApi, SystemLogsApi, SystemPingApi,
    SystemReleasesApi, SystemStatusApi, SystemTasksApi, SystemSettingsApi {

    val responses: MutableMap<String, JsonObject> = mutableMapOf()
    val calls: MutableList<BazarrSystemCall> = mutableListOf()

    private suspend fun before(operation: String, args: Map<String, Any?>): Pair<
        BazarrSystemCall, Int?
        > {
        val call = BazarrSystemCall(operation, args)
        calls.add(call)
        latency(call)
        failure(call)?.let { throw it }
        val status = errorStatus(call)
        require(status == null || status in 400..599)
        return call to status
    }

    private suspend fun read(operation: String, vararg args: Pair<String, Any?>): JsonObject {
        val (_, status) = before(operation, args.toMap())
        if (status != null) {
            throw HttpException(Response.error<JsonObject>(status, "{}".toResponseBody()))
        }
        return responses[operation] ?: JsonObject(emptyMap())
    }

    private suspend fun write(operation: String, vararg args: Pair<String, Any?>): Response<Unit> {
        val (call, status) = before(operation, args.toMap())
        if (status != null) return Response.error(status, "{}".toResponseBody())
        mutate(call, responses)
        return Response.success(204, Unit)
    }

    /** The webhook probe reports its outcome through HTTP 200, matching the spec's success code. */
    private suspend fun probe(operation: String, vararg args: Pair<String, Any?>): Response<Unit> {
        val (call, status) = before(operation, args.toMap())
        if (status != null) return Response.error(status, "{}".toResponseBody())
        mutate(call, responses)
        return Response.success(200, Unit)
    }

    override suspend fun getSystemAnnouncements() = read("get_system_announcements")

    override suspend fun postSystemAnnouncements(hash: String) =
        write("post_system_announcements", "hash" to hash)

    override suspend fun getSystemBackups() = read("get_system_backups")

    override suspend fun postSystemBackups() = write("post_system_backups")

    override suspend fun patchSystemBackups(filename: String) =
        write("patch_system_backups", "filename" to filename)

    override suspend fun deleteSystemBackups(filename: String) =
        write("delete_system_backups", "filename" to filename)

    override suspend fun getSystemHealth() = read("get_system_health")

    override suspend fun getSystemJobs(id: Int?, status: String?) =
        read("get_system_jobs", "id" to id, "status" to status)

    override suspend fun postSystemJobs(id: Int, action: String) =
        write("post_system_jobs", "id" to id, "action" to action)

    override suspend fun patchSystemJobs(queueName: String) =
        write("patch_system_jobs", "queueName" to queueName)

    override suspend fun deleteSystemJobs(id: Int) =
        write("delete_system_jobs", "id" to id)

    override suspend fun getLanguages(history: String?) =
        read("get_languages", "history" to history)

    override suspend fun getLanguagesProfiles() = read("get_languages_profiles")

    override suspend fun getSystemLogs() = read("get_system_logs")

    override suspend fun deleteSystemLogs() = write("delete_system_logs")

    override suspend fun getSystemPing() = read("get_system_ping")

    override suspend fun getSystemReleases() = read("get_system_releases")

    override suspend fun getSystemStatus() = read("get_system_status")

    override suspend fun getSystemTasks() = read("get_system_tasks")

    override suspend fun postSystemTasks(taskId: String) =
        write("post_system_tasks", "taskid" to taskId)

    override suspend fun postSystemWebhookTest() = probe("post_system_webhook_test")
}
