package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ApiInfoResource
import dev.appolon.homelab.arr.api.prowlarr.model.BackupResource
import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import dev.appolon.homelab.arr.api.prowlarr.model.LogFileResource
import dev.appolon.homelab.arr.api.prowlarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.PingResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import dev.appolon.homelab.arr.api.prowlarr.model.UpdateResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Every operation under the eleven system-group tags runs one happy path and
 * one error path against MockWebServer. JSON fixtures are synthetic values
 * that validate against the bundled OpenAPI schemas; the spec ships no
 * examples. Route dumps, log file contents and static pages ride outside the
 * JSON converter as raw [ResponseBody]s because the spec declares text or no
 * content for them.
 */
@RunWith(Parameterized::class)
class ProwlarrSystemApiTest(private val case: SystemCase) {
    @Test
    fun happyPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val fixture = case.fixture?.let(::fixtureText)
            server.enqueue(
                if (fixture != null) {
                    MockResponse().setResponseCode(200).setBody(fixture)
                } else {
                    MockResponse().setResponseCode(200)
                }
            )
            val result = case.call(retrofit(server))
            if (result is Response<*>) {
                assertEquals(200, result.code())
                if (case.rawBody) {
                    assertEquals(fixture, (result.body() as ResponseBody).string())
                } else {
                    assertEquals(Unit, result.body())
                }
            } else {
                val decode = requireNotNull(case.decode) { "plain calls need a decoder" }
                assertEquals(decode(fixture!!), result)
            }
            val request = server.takeRequest()
            assertEquals(case.method, request.method)
            val url = requireNotNull(request.requestUrl)
            assertEquals("/prefix/${case.path.trimStart('/')}", url.encodedPath)
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

    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/prefix/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    companion object {
        private fun fixtureText(path: String): String = requireNotNull(
            javaClass.getResource("/fixtures/prowlarr/$path")
        ) { "missing fixture $path" }.readText()

        class SystemCase(
            val method: String,
            val path: String,
            val query: Map<String, List<String>> = emptyMap(),
            val fixture: String? = null,
            val rawBody: Boolean = false,
            val decode: ((String) -> Any?)? = null,
            val call: suspend (Retrofit) -> Any?,
        ) {
            override fun toString(): String = "$method /${path.trimStart('/')}"
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): List<SystemCase> {
            val cases = mutableListOf<SystemCase>()

            fun add(
                method: String,
                path: String,
                query: Map<String, List<String>> = emptyMap(),
                fixture: String? = null,
                rawBody: Boolean = false,
                decode: ((String) -> Any?)? = null,
                call: suspend (Retrofit) -> Any?,
            ) {
                cases += SystemCase(method, path, query, fixture, rawBody, decode, call)
            }

            add(
                "GET", "api/v1/system/status",
                fixture = "system/response.json",
                decode = { text -> ArrJson.decodeFromString<SystemResource>(text) },
            ) {
                ProwlarrSystemApis.system(it).getSystemStatus()
            }
            add(
                "GET", "api/v1/system/routes",
                fixture = "system/routes.txt",
                decode = { text -> text },
            ) {
                ProwlarrSystemApis.system(it).getSystemRoutes().string()
            }
            add(
                "GET", "api/v1/system/routes/duplicate",
                fixture = "system/routes-duplicate.json",
                decode = { text -> ArrJson.parseToJsonElement(text) },
            ) {
                ProwlarrSystemApis.system(it).getSystemRoutesDuplicate()
            }
            add("POST", "api/v1/system/shutdown") {
                ProwlarrSystemApis.system(it).shutdown()
            }
            add("POST", "api/v1/system/restart") {
                ProwlarrSystemApis.system(it).restart()
            }

            val backups: (String) -> Any? = { text ->
                ArrJson.decodeFromString<List<BackupResource>>(text)
            }
            add("GET", "api/v1/system/backup", fixture = "backup/list.json", decode = backups) {
                ProwlarrSystemApis.backup(it).getBackups()
            }
            add("DELETE", "api/v1/system/backup/3") {
                ProwlarrSystemApis.backup(it).deleteBackup(3)
            }
            add("POST", "api/v1/system/backup/restore/3") {
                ProwlarrSystemApis.backup(it).restoreBackup(3)
            }
            add("POST", "api/v1/system/backup/restore/upload") {
                ProwlarrSystemApis.backup(it).restoreBackupUpload()
            }

            add(
                "GET", "api/v1/health",
                fixture = "health/list.json",
                decode = { text -> ArrJson.decodeFromString<List<HealthResource>>(text) },
            ) {
                ProwlarrSystemApis.health(it).getHealth()
            }

            add(
                "GET", "api/v1/log",
                query = linkedMapOf(
                    "page" to listOf("2"),
                    "pageSize" to listOf("2"),
                    "sortKey" to listOf("time"),
                    "sortDirection" to listOf("descending"),
                    "level" to listOf("info"),
                ),
                fixture = "log/paged.json",
                decode = { text -> ArrJson.decodeFromString<LogResourcePagingResource>(text) },
            ) {
                ProwlarrSystemApis.log(it).getLog(2, 2, "time", SortDirection.DESCENDING, "info")
            }

            val logFiles: (String) -> Any? = { text ->
                ArrJson.decodeFromString<List<LogFileResource>>(text)
            }
            add("GET", "api/v1/log/file", fixture = "log-file/list.json", decode = logFiles) {
                ProwlarrSystemApis.logFile(it).getLogFiles()
            }
            add(
                "GET", "api/v1/log/file/prowlarr.txt",
                fixture = "log-file/contents.txt",
                decode = { text -> text },
            ) {
                ProwlarrSystemApis.logFile(it).getLogFileContents("prowlarr.txt").string()
            }

            add(
                "GET", "api/v1/update",
                fixture = "update/list.json",
                decode = { text -> ArrJson.decodeFromString<List<UpdateResource>>(text) },
            ) {
                ProwlarrSystemApis.update(it).getUpdates()
            }

            add(
                "GET", "api/v1/log/file/update",
                fixture = "update-log-file/list.json",
                decode = logFiles,
            ) {
                ProwlarrSystemApis.updateLogFile(it).getUpdateLogFiles()
            }
            add(
                "GET", "api/v1/log/file/update/prowlarr.update.2026-09-01.txt",
                fixture = "update-log-file/contents.txt",
                decode = { text -> text },
            ) {
                ProwlarrSystemApis.updateLogFile(it)
                    .getUpdateLogFileContents("prowlarr.update.2026-09-01.txt")
                    .string()
            }

            val tasks: (String) -> Any? = { text ->
                ArrJson.decodeFromString<List<TaskResource>>(text)
            }
            add("GET", "api/v1/system/task", fixture = "task/list.json", decode = tasks) {
                ProwlarrSystemApis.task(it).getTasks()
            }
            add(
                "GET", "api/v1/system/task/1",
                fixture = "task/response.json",
                decode = { text -> ArrJson.decodeFromString<TaskResource>(text) },
            ) {
                ProwlarrSystemApis.task(it).getTask(1)
            }

            add(
                "GET", "ping",
                fixture = "ping/response.json",
                decode = { text -> ArrJson.decodeFromString<PingResource>(text) },
            ) {
                ProwlarrSystemApis.ping(it).ping()
            }
            add("HEAD", "ping") {
                ProwlarrSystemApis.ping(it).pingHead()
            }

            add(
                "GET", "api",
                fixture = "api-info/response.json",
                decode = { text -> ArrJson.decodeFromString<ApiInfoResource>(text) },
            ) {
                ProwlarrSystemApis.apiInfo(it).getApiInfo()
            }

            add(
                "GET", "login",
                fixture = "static-resource/page.html",
                rawBody = true,
            ) {
                ProwlarrSystemApis.staticResource(it).getLoginPage()
            }
            add(
                "GET", "content/favicon.ico",
                fixture = "static-resource/page.html",
                rawBody = true,
            ) {
                ProwlarrSystemApis.staticResource(it).getContent("favicon.ico")
            }
            add(
                "GET", "/",
                fixture = "static-resource/page.html",
                rawBody = true,
            ) {
                ProwlarrSystemApis.staticResource(it).getRoot()
            }
            add(
                "GET", "favicon.ico",
                fixture = "static-resource/page.html",
                rawBody = true,
            ) {
                ProwlarrSystemApis.staticResource(it).getStaticResource("favicon.ico")
            }

            return cases
        }
    }
}
