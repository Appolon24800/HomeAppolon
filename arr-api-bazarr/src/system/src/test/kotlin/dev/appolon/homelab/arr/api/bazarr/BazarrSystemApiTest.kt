package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each documented operation exercises both its request contract and an HTTP failure. */
@RunWith(Parameterized::class)
class BazarrSystemApiTest(private val case: SystemCase) {
    @Test
    fun happyPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val fixture = requireNotNull(javaClass.getResource(
                "/fixtures/bazarr/${case.tag}/response.json"
            )).readText()
            server.enqueue(MockResponse().setResponseCode(case.status).apply {
                if (case.status != 204) setBody(fixture)
            })
            val result = case.call(retrofit(server))
            if (result is Response<*>) {
                assertEquals(case.status, result.code())
            } else {
                assertEquals(ArrJson.parseToJsonElement(fixture), result)
            }
            val request = server.takeRequest()
            assertEquals(case.method, request.method)
            val url = requireNotNull(request.requestUrl)
            assertEquals("/prefix/api/${case.path}", url.encodedPath)
            assertEquals(case.query.keys, url.queryParameterNames)
            case.query.forEach { (key, values) ->
                assertEquals(values, url.queryParameterValues(key))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun errorPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            try {
                val result = case.call(retrofit(server))
                assertTrue("Expected status-bearing failure", result is Response<*>)
                assertEquals(401, (result as Response<*>).code())
            } catch (error: HttpException) {
                assertEquals(401, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun omittedOptionalQueriesLetTheServerChooseDefaults() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{}"))
            val retrofit = retrofit(server)
            BazarrSystemApis.jobs(retrofit).getSystemJobs()
            assertNull(server.takeRequest().requestUrl!!.query)
            server.enqueue(MockResponse().setBody("{}"))
            BazarrSystemApis.languages(retrofit).getLanguages()
            assertNull(server.takeRequest().requestUrl!!.query)
        } finally {
            server.shutdown()
        }
    }

    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/prefix/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val cases = mutableListOf<SystemCase>()
            fun add(
                tag: String, method: String, path: String, status: Int = 200,
                query: Map<String, List<String>> = emptyMap(),
                call: suspend (Retrofit) -> Any,
            ) {
                cases += SystemCase(tag, method, path, status, query, call)
            }
            add("announcements", "GET", "system/announcements") {
                BazarrSystemApis.announcements(it).getSystemAnnouncements()
            }
            add("announcements", "POST", "system/announcements", 204,
                mapOf("hash" to listOf("spec-announcement-hash"))) {
                BazarrSystemApis.announcements(it).postSystemAnnouncements("spec-announcement-hash")
            }
            add("backups", "GET", "system/backups") {
                BazarrSystemApis.backups(it).getSystemBackups()
            }
            add("backups", "POST", "system/backups", 204) {
                BazarrSystemApis.backups(it).postSystemBackups()
            }
            add("backups", "PATCH", "system/backups", 204,
                mapOf("filename" to listOf("bazarr_v1_backup.zip"))) {
                BazarrSystemApis.backups(it).patchSystemBackups("bazarr_v1_backup.zip")
            }
            add("backups", "DELETE", "system/backups", 204,
                mapOf("filename" to listOf("bazarr_v1_backup.zip"))) {
                BazarrSystemApis.backups(it).deleteSystemBackups("bazarr_v1_backup.zip")
            }
            add("health", "GET", "system/health") {
                BazarrSystemApis.health(it).getSystemHealth()
            }
            add("jobs", "GET", "system/jobs", query = mapOf(
                "id" to listOf("7"), "status" to listOf("running"),
            )) { BazarrSystemApis.jobs(it).getSystemJobs(7, "running") }
            add("jobs", "POST", "system/jobs", 204, mapOf(
                "id" to listOf("7"), "action" to listOf("force_start"),
            )) { BazarrSystemApis.jobs(it).postSystemJobs(7, "force_start") }
            add("jobs", "PATCH", "system/jobs", 204,
                mapOf("queueName" to listOf("update_listeners"))) {
                BazarrSystemApis.jobs(it).patchSystemJobs("update_listeners")
            }
            add("jobs", "DELETE", "system/jobs", 204, mapOf("id" to listOf("7"))) {
                BazarrSystemApis.jobs(it).deleteSystemJobs(7)
            }
            add("languages", "GET", "system/languages",
                query = mapOf("history" to listOf("English"))) {
                BazarrSystemApis.languages(it).getLanguages("English")
            }
            add("languages-profiles", "GET", "system/languages/profiles") {
                BazarrSystemApis.languageProfiles(it).getLanguagesProfiles()
            }
            add("logs", "GET", "system/logs") {
                BazarrSystemApis.logs(it).getSystemLogs()
            }
            add("logs", "DELETE", "system/logs", 204) {
                BazarrSystemApis.logs(it).deleteSystemLogs()
            }
            add("ping", "GET", "system/ping") {
                BazarrSystemApis.ping(it).getSystemPing()
            }
            add("releases", "GET", "system/releases") {
                BazarrSystemApis.releases(it).getSystemReleases()
            }
            add("status", "GET", "system/status") {
                BazarrSystemApis.status(it).getSystemStatus()
            }
            add("tasks", "GET", "system/tasks") {
                BazarrSystemApis.tasks(it).getSystemTasks()
            }
            add("tasks", "POST", "system/tasks", 204,
                mapOf("taskid" to listOf("sync-series"))) {
                BazarrSystemApis.tasks(it).postSystemTasks("sync-series")
            }
            return cases.map { arrayOf(it) }
        }
    }
}

/** An operation's wire expectations are independent of Retrofit's implementation. */
data class SystemCase(
    val tag: String,
    val method: String,
    val path: String,
    val status: Int,
    val query: Map<String, List<String>>,
    val call: suspend (Retrofit) -> Any,
) {
    override fun toString() = "$method $path"
}
