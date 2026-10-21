package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.BackupResource
import dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.lidarr.model.HealthResource
import dev.appolon.homelab.arr.api.lidarr.model.LocalizationResource
import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import dev.appolon.homelab.arr.api.lidarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import dev.appolon.homelab.arr.api.lidarr.model.SystemResource
import dev.appolon.homelab.arr.api.lidarr.model.TaskResource
import dev.appolon.homelab.arr.api.lidarr.model.UpdateResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun systemFixture(path: String): String = checkNotNull(
    LidarrSystemEndpointTest::class.java.getResourceAsStream("/fixtures/lidarr/$path.json")
).bufferedReader().use { it.readText() }

internal inline fun <reified T> systemDto(path: String): T =
    ArrJson.decodeFromString(systemFixture(path))

internal fun systemRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/lidarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Each row is one spec operation under the system and connect tag groups. */
data class SystemEndpointCase(
    val name: String,
    val method: String,
    val path: String,
    val response: String = "",
    val queries: Map<String, List<String>> = emptyMap(),
    val body: JsonElement? = null,
    val bodyContains: List<String> = emptyList(),
    val call: suspend (Retrofit) -> Unit,
) {
    override fun toString(): String = name
}

@RunWith(Parameterized::class)
class LidarrSystemEndpointTest(private val case: SystemEndpointCase) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun happyPath() = runTest {
        server.enqueue(MockResponse().setBody(case.response))
        case.call(systemRetrofit(server))
        val request = server.takeRequest()
        assertEquals(case.method, request.method)
        assertEquals("/lidarr/${case.path}", request.requestUrl!!.encodedPath)
        assertEquals(case.queries.keys, request.requestUrl!!.queryParameterNames)
        case.queries.forEach { (name, values) ->
            assertEquals(values, request.requestUrl!!.queryParameterValues(name))
        }
        if (case.body != null) {
            assertEquals(case.body, ArrJson.parseToJsonElement(request.body.readUtf8()))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        } else if (case.bodyContains.isNotEmpty()) {
            val raw = request.body.readUtf8()
            case.bodyContains.forEach { part -> assertTrue("$part missing in $raw", part in raw) }
        } else {
            assertEquals(0L, request.bodySize)
        }
    }

    companion object {
        private val apiInfoJson = "{\"currentAPIVersion\":1}"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun operations(): List<Array<Any>> {
            val backups = systemDto<List<BackupResource>>("backup/getBackups")
            val diskSpace = systemDto<List<DiskSpaceResource>>("diskspace/getDiskSpace")
            val health = systemDto<List<HealthResource>>("health/getHealth")
            val localization = systemDto<LocalizationResource>("localization/getLocalization")
            val log = systemDto<LogResourcePagingResource>("log/getLog")
            val logFiles = systemDto<List<LogFileResource>>("logfile/getLogFiles")
            val status = systemDto<SystemResource>("system/getStatus")
            val tasks = systemDto<List<TaskResource>>("task/getTasks")
            val updates = systemDto<List<UpdateResource>>("update/getUpdates")
            val updateLogFiles = systemDto<List<LogFileResource>>("updatelogfile/getUpdateLogFiles")
            fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
                pairs.groupBy({ it.first }, { it.second })
            val duplicateRoutes = "[]"
            val fileType = "\"folder\""
            val mediaFiles = "[\"01 Example.mp3\"]"
            return listOf(
                SystemEndpointCase(
                    "getApiInfo", "GET", "api", apiInfoJson,
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(apiInfoJson),
                        LidarrSystemApis.apiInfo(r).getApiInfo(),
                    )
                },
                SystemEndpointCase(
                    "login", "POST", "login",
                    queries = query("returnUrl" to "/"),
                    bodyContains = listOf("username", "admin", "password", "secret", "rememberMe"),
                ) { r ->
                    LidarrSystemApis.authentication(r).login(
                        "admin".toTextPart(),
                        "secret".toTextPart(),
                        "true".toTextPart(),
                        returnUrl = "/",
                    )
                },
                SystemEndpointCase(
                    "logout", "GET", "logout",
                ) { r -> LidarrSystemApis.authentication(r).logout() },
                SystemEndpointCase(
                    "getBackups", "GET", "api/v1/system/backup",
                    systemFixture("backup/getBackups"),
                ) { r -> assertEquals(backups, LidarrSystemApis.backup(r).getBackups()) },
                SystemEndpointCase(
                    "deleteBackup", "DELETE", "api/v1/system/backup/1",
                ) { r -> LidarrSystemApis.backup(r).deleteBackup(1) },
                SystemEndpointCase(
                    "restoreBackup", "POST", "api/v1/system/backup/restore/1",
                ) { r -> LidarrSystemApis.backup(r).restoreBackup(1) },
                SystemEndpointCase(
                    "restoreUploadedBackup", "POST", "api/v1/system/backup/restore/upload",
                ) { r -> LidarrSystemApis.backup(r).restoreUploadedBackup() },
                SystemEndpointCase(
                    "getDiskSpace", "GET", "api/v1/diskspace",
                    systemFixture("diskspace/getDiskSpace"),
                ) { r -> assertEquals(diskSpace, LidarrSystemApis.diskSpace(r).getDiskSpace()) },
                SystemEndpointCase(
                    "getHealth", "GET", "api/v1/health",
                    systemFixture("health/getHealth"),
                ) { r -> assertEquals(health, LidarrSystemApis.health(r).getHealth()) },
                SystemEndpointCase(
                    "getLog", "GET", "api/v1/log",
                    systemFixture("log/getLog"),
                    query(
                        "page" to "1", "pageSize" to "10", "sortKey" to "time",
                        "sortDirection" to "descending", "level" to "info",
                    ),
                ) { r ->
                    assertEquals(
                        log, LidarrSystemApis.log(r).getLog(
                            1, 10, "time", SortDirection.DESCENDING, "info",
                        )
                    )
                },
                SystemEndpointCase(
                    "getLogFiles", "GET", "api/v1/log/file",
                    systemFixture("logfile/getLogFiles"),
                ) { r -> assertEquals(logFiles, LidarrSystemApis.logFile(r).getLogFiles()) },
                SystemEndpointCase(
                    "getLogFile", "GET", "api/v1/log/file/lidarr.txt",
                    "2026-10-01T12:00:00Z Info Server starting\n",
                ) { r ->
                    assertEquals(
                        "2026-10-01T12:00:00Z Info Server starting\n",
                        LidarrSystemApis.logFile(r).getLogFile("lidarr.txt").body()!!.string(),
                    )
                },
                SystemEndpointCase(
                    "getStatus", "GET", "api/v1/system/status",
                    systemFixture("system/getStatus"),
                ) { r -> assertEquals(status, LidarrSystemApis.system(r).getStatus()) },
                SystemEndpointCase(
                    "getRoutes", "GET", "api/v1/system/routes",
                    systemFixture("system/getRoutes"),
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(systemFixture("system/getRoutes")),
                        LidarrSystemApis.system(r).getRoutes(),
                    )
                },
                SystemEndpointCase(
                    "getDuplicateRoutes", "GET", "api/v1/system/routes/duplicate",
                    duplicateRoutes,
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(duplicateRoutes),
                        LidarrSystemApis.system(r).getDuplicateRoutes(),
                    )
                },
                SystemEndpointCase(
                    "restart", "POST", "api/v1/system/restart",
                ) { r -> LidarrSystemApis.system(r).restart() },
                SystemEndpointCase(
                    "shutdown", "POST", "api/v1/system/shutdown",
                ) { r -> LidarrSystemApis.system(r).shutdown() },
                SystemEndpointCase(
                    "getTasks", "GET", "api/v1/system/task",
                    systemFixture("task/getTasks"),
                ) { r -> assertEquals(tasks, LidarrSystemApis.task(r).getTasks()) },
                SystemEndpointCase(
                    "getTask", "GET", "api/v1/system/task/1",
                    ArrJson.encodeToString(tasks.first()),
                ) { r -> assertEquals(tasks.first(), LidarrSystemApis.task(r).getTask(1)) },
                SystemEndpointCase(
                    "getUpdates", "GET", "api/v1/update",
                    systemFixture("update/getUpdates"),
                ) { r -> assertEquals(updates, LidarrSystemApis.update(r).getUpdates()) },
                SystemEndpointCase(
                    "getUpdateLogFiles", "GET", "api/v1/log/file/update",
                    systemFixture("updatelogfile/getUpdateLogFiles"),
                ) { r ->
                    assertEquals(
                        updateLogFiles, LidarrSystemApis.updateLogFile(r).getUpdateLogFiles()
                    )
                },
                SystemEndpointCase(
                    "getUpdateLogFile", "GET", "api/v1/log/file/update/lidarr.update.txt",
                    "2026-10-01T12:30:00Z Info Update installed\n",
                ) { r ->
                    assertEquals(
                        "2026-10-01T12:30:00Z Info Update installed\n",
                        LidarrSystemApis.updateLogFile(r)
                            .getUpdateLogFile("lidarr.update.txt").body()!!.string(),
                    )
                },
                SystemEndpointCase(
                    "getLocalization", "GET", "api/v1/localization",
                    systemFixture("localization/getLocalization"),
                ) { r ->
                    assertEquals(
                        localization, LidarrSystemApis.localization(r).getLocalization()
                    )
                },
                SystemEndpointCase(
                    "getFileSystem", "GET", "api/v1/filesystem",
                    systemFixture("filesystem/getFileSystem"),
                    query(
                        "path" to "/music", "includeFiles" to "true",
                        "allowFoldersWithoutTrailingSlashes" to "false",
                    ),
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(systemFixture("filesystem/getFileSystem")),
                        LidarrSystemApis.fileSystem(r).getFileSystem("/music", true, false),
                    )
                },
                SystemEndpointCase(
                    "getFileSystemType", "GET", "api/v1/filesystem/type",
                    fileType,
                    query("path" to "/music"),
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(fileType),
                        LidarrSystemApis.fileSystem(r).getFileSystemType("/music"),
                    )
                },
                SystemEndpointCase(
                    "getMediaFiles", "GET", "api/v1/filesystem/mediafiles",
                    mediaFiles,
                    query("path" to "/music/artist"),
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(mediaFiles),
                        LidarrSystemApis.fileSystem(r).getMediaFiles("/music/artist"),
                    )
                },
            ).map { case -> arrayOf<Any>(case) }
        }
    }
}

private fun String.toTextPart(): okhttp3.RequestBody = toRequestBody(
    "text/plain".toMediaType()
)
