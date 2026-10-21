package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.BackupType
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.radarr.model.ParsedMovieInfo
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.api.radarr.model.UpdateResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each spec operation has its own fixture and verifies the base-path prefix. */
class RadarrSystemContractTest {
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use { it.readText() }

    @Test fun apiInfoHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("apiinfo", "apiInfo")))
            val api = RadarrSystemApis.apiInfo(retrofit(server))
            val result = api.apiInfo()
            assertEquals("v3", result.current)
            assertEquals(listOf("v0"), result.deprecated)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun loginSsoHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(302))
            val api = RadarrSystemApis.authentication(retrofit(server))
            val result = api.loginSso(returnUrl = "/settings")
            assertEquals(302, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/login/sso", request.requestUrl!!.encodedPath)
            assertEquals("/settings", request.requestUrl!!.queryParameter("returnUrl"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun loginHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(302))
            val api = RadarrSystemApis.authentication(retrofit(server))
            val result = api.login(
                returnUrl = "/",
                username = "user".toRequestBody("text/plain".toMediaType()),
                password = "pass".toRequestBody("text/plain".toMediaType()),
                rememberMe = "true".toRequestBody("text/plain".toMediaType()),
            )
            assertEquals(302, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/login", request.requestUrl!!.encodedPath)
            assertEquals("/", request.requestUrl!!.queryParameter("returnUrl"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"username\""))
            assertTrue(body.contains("user"))
            assertTrue(body.contains("name=\"password\""))
            assertTrue(body.contains("name=\"rememberMe\""))
        } finally {
            server.shutdown()
        }
    }

    @Test fun logoutHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(302))
            val api = RadarrSystemApis.authentication(retrofit(server))
            val result = api.logout()
            assertEquals(302, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/logout", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun backupsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("backup", "backups")))
            val api = RadarrSystemApis.backup(retrofit(server))
            val result = api.backups()
            assertEquals(1, result.size)
            assertEquals(BackupType.SCHEDULED, result.single().type)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/system/backup", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeBackupHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrSystemApis.backup(retrofit(server))
            val result = api.removeBackup(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/system/backup/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun restoreBackupHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("backup", "restoreBackup")))
            val api = RadarrSystemApis.backup(retrofit(server))
            val result = api.restoreBackup(id = 1)
            assertTrue(result.body()!!.restartRequired)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/system/backup/restore/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun restoreBackupUploadHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("backup", "restoreBackupUpload")))
            val api = RadarrSystemApis.backup(retrofit(server))
            val result = api.restoreBackupUpload()
            assertFalse(result.body()!!.restartRequired)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/system/backup/restore/upload",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun diskSpaceHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("diskspace", "diskspace")))
            val api = RadarrSystemApis.diskSpace(retrofit(server))
            val result = api.diskSpace()
            assertEquals(2, result.size)
            assertEquals(DiskSpaceResource(id = 1, path = "/", label = "Root",
                freeSpace = 5000000000, totalSpace = 20000000000), result.first())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/diskspace", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun fileSystemHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("filesystem", "fileSystem")))
            val api = RadarrSystemApis.fileSystem(retrofit(server))
            val result = api.fileSystem(
                path = "/movies",
                includeFiles = true,
                allowFoldersWithoutTrailingSlashes = false,
            )
            assertEquals("/movies", result.parent)
            assertEquals(1, result.directories!!.size)
            assertEquals(1, result.files!!.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/filesystem", request.requestUrl!!.encodedPath)
            assertEquals("/movies", request.requestUrl!!.queryParameter("path"))
            assertEquals("true", request.requestUrl!!.queryParameter("includeFiles"))
            assertEquals("false",
                request.requestUrl!!.queryParameter("allowFoldersWithoutTrailingSlashes"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun fileSystemTypeHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("filesystem", "fileSystemType")))
            val api = RadarrSystemApis.fileSystem(retrofit(server))
            val result = api.fileSystemType(path = "/movies")
            assertEquals(FileSystemEntityType.FOLDER, result.type)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/filesystem/type", request.requestUrl!!.encodedPath)
            assertEquals("/movies", request.requestUrl!!.queryParameter("path"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun fileSystemMediaFilesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("filesystem", "fileSystemMediaFiles")))
            val api = RadarrSystemApis.fileSystem(retrofit(server))
            val result = api.fileSystemMediaFiles(path = "/movies")
            assertEquals(1, result.size)
            assertEquals("Movie Title (2026).mkv", result.single().name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/filesystem/mediafiles",
                request.requestUrl!!.encodedPath)
            assertEquals("/movies", request.requestUrl!!.queryParameter("path"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun healthHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("health", "health")))
            val api = RadarrSystemApis.health(retrofit(server))
            val result = api.health()
            assertEquals(1, result.size)
            assertEquals(HealthCheckResult.WARNING, result.single().type)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/health", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun localizationHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("localization", "localization")))
            val api = RadarrSystemApis.localization(retrofit(server))
            val result = api.localization()
            assertEquals("Add", result["common"]!!.jsonObject["add"]!!.jsonPrimitive.content)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/localization", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun localizationLanguageHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("localization", "localizationLanguage")))
            val api = RadarrSystemApis.localization(retrofit(server))
            val result = api.localizationLanguage()
            assertEquals("en", result.identifier)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/localization/language",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun logHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("log", "log")))
            val api = RadarrSystemApis.log(retrofit(server))
            val result = api.log(
                page = 1,
                pageSize = 10,
                sortKey = "time",
                sortDirection = SortDirection.DEFAULT,
                level = "info",
            )
            assertEquals(1, result.totalRecords)
            assertEquals("info", result.records!!.single().level)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/log", request.requestUrl!!.encodedPath)
            assertEquals("1", request.requestUrl!!.queryParameter("page"))
            assertEquals("10", request.requestUrl!!.queryParameter("pageSize"))
            assertEquals("time", request.requestUrl!!.queryParameter("sortKey"))
            assertEquals("default", request.requestUrl!!.queryParameter("sortDirection"))
            assertEquals("info", request.requestUrl!!.queryParameter("level"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun logFilesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("logfile", "logFiles")))
            val api = RadarrSystemApis.logFile(retrofit(server))
            val result = api.logFiles()
            assertEquals(1, result.size)
            assertEquals("radarr.txt", result.single().filename)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/log/file", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun logFileContentsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody("2026-10-01T00:00:00Z|Info|Test line"))
            val api = RadarrSystemApis.logFile(retrofit(server))
            val result = api.logFileContents(filename = "radarr.txt")
            assertEquals("2026-10-01T00:00:00Z|Info|Test line", result.string())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/log/file/radarr.txt",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun parseHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("parse", "parse")))
            val api = RadarrSystemApis.parse(retrofit(server))
            val result = api.parse(title = "Movie.Title.2026.1080p.BluRay.x264-GROUP")
            val info: ParsedMovieInfo = result.parsedMovieInfo!!
            assertEquals("Movie Title", info.movieTitles!!.single())
            assertEquals(2026, info.year)
            assertEquals("GROUP", info.releaseGroup)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/parse", request.requestUrl!!.encodedPath)
            assertEquals("Movie.Title.2026.1080p.BluRay.x264-GROUP",
                request.requestUrl!!.queryParameter("title"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun pingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("ping", "ping")))
            val api = RadarrSystemApis.ping(retrofit(server))
            val result = api.ping()
            assertEquals(200, result.code())
            assertEquals("ok", result.body()!!.status)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/ping", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun rootFoldersHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("rootfolder", "rootFolders")))
            val api = RadarrSystemApis.rootFolder(retrofit(server))
            val result = api.rootFolders()
            assertEquals(1, result.size)
            assertTrue(result.single().accessible)
            assertEquals(1, result.single().unmappedFolders!!.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/rootfolder", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createRootFolderHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("rootfolder", "createRootFolder")))
            val api = RadarrSystemApis.rootFolder(retrofit(server))
            val result = api.createRootFolder(RootFolderResource(path = "/movies"))
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/rootfolder", request.requestUrl!!.encodedPath)
            assertEquals(RootFolderResource(path = "/movies"),
                ArrJson.decodeFromString<RootFolderResource>(request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeRootFolderHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrSystemApis.rootFolder(retrofit(server))
            val result = api.removeRootFolder(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/rootfolder/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun getRootFolderHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("rootfolder", "getRootFolder")))
            val api = RadarrSystemApis.rootFolder(retrofit(server))
            val result = api.rootFolder(id = 1)
            assertEquals(200, result.code())
            assertEquals("/movies", result.body()!!.path)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/rootfolder/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun systemStatusHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("system", "systemStatus")))
            val api = RadarrSystemApis.system(retrofit(server))
            val result = api.systemStatus()
            assertEquals("Radarr", result.appName)
            assertEquals("5.30.0.8940", result.version)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/system/status", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun systemRoutesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody("[{\"path\":\"/api/v3/system/status\"}]"))
            val api = RadarrSystemApis.system(retrofit(server))
            val result = api.systemRoutes()
            assertEquals("[{\"path\":\"/api/v3/system/status\"}]", result.string())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/system/routes", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun systemRoutesDuplicateHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("system", "systemRoutesDuplicate")))
            val api = RadarrSystemApis.system(retrofit(server))
            val result = api.systemRoutesDuplicate()
            assertEquals(listOf("GET"), result["/api/v3/system/status"])
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/system/routes/duplicate",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun shutdownHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("system", "shutdown")))
            val api = RadarrSystemApis.system(retrofit(server))
            val result = api.shutdown()
            assertTrue(result.shuttingDown)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/system/shutdown", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun restartHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("system", "restart")))
            val api = RadarrSystemApis.system(retrofit(server))
            val result = api.restart()
            assertTrue(result.restarting)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/system/restart", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun tagsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("tag", "tags")))
            val api = RadarrSystemApis.tag(retrofit(server))
            val result = api.tags()
            assertEquals(listOf(TagResource(id = 1, label = "imdb-top-250")), result)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/tag", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createTagHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("tag", "createTag")))
            val api = RadarrSystemApis.tag(retrofit(server))
            val result = api.createTag(TagResource(label = "imdb-top-250"))
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/tag", request.requestUrl!!.encodedPath)
            assertEquals(TagResource(label = "imdb-top-250"),
                ArrJson.decodeFromString<TagResource>(request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateTagHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("tag", "updateTag")))
            val api = RadarrSystemApis.tag(retrofit(server))
            val result = api.updateTag(id = "1", body = TagResource(id = 1, label = "4k-remux"))
            assertEquals(202, result.code())
            assertEquals("4k-remux", result.body()!!.label)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/tag/1", request.requestUrl!!.encodedPath)
            assertEquals(TagResource(id = 1, label = "4k-remux"),
                ArrJson.decodeFromString<TagResource>(request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeTagHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrSystemApis.tag(retrofit(server))
            val result = api.removeTag(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/tag/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun getTagHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("tag", "getTag")))
            val api = RadarrSystemApis.tag(retrofit(server))
            val result = api.tag(id = 1)
            assertEquals(200, result.code())
            assertEquals("imdb-top-250", result.body()!!.label)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/tag/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun tagDetailsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("tagdetails", "tagDetails")))
            val api = RadarrSystemApis.tagDetails(retrofit(server))
            val result = api.tagDetails()
            assertEquals(1, result.size)
            assertEquals(listOf(1), result.single().autoTagIds)
            assertEquals(listOf(1, 2), result.single().movieIds)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/tag/detail", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun getTagDetailHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("tagdetails", "getTagDetail")))
            val api = RadarrSystemApis.tagDetails(retrofit(server))
            val result = api.tagDetail(id = 1)
            assertEquals(200, result.code())
            assertEquals("imdb-top-250", result.body()!!.label)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/tag/detail/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun tasksHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("task", "tasks")))
            val api = RadarrSystemApis.task(retrofit(server))
            val result = api.tasks()
            assertEquals(1, result.size)
            assertEquals("CheckForFinishedDownloads", result.single().taskName)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/system/task", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun getTaskHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("task", "getTask")))
            val api = RadarrSystemApis.task(retrofit(server))
            val result = api.task(id = 1)
            assertEquals(200, result.code())
            assertEquals(1, result.body()!!.interval)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/system/task/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updatesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("update", "updates")))
            val api = RadarrSystemApis.update(retrofit(server))
            val result: List<UpdateResource> = api.updates()
            assertEquals(1, result.size)
            assertTrue(result.single().latest)
            assertEquals(listOf("Added something"),
                result.single().changes!!.newFeatures)
            assertEquals(listOf("Fixed something"), result.single().changes!!.fixed)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/update", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateLogFilesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("updatelogfile", "updateLogFiles")))
            val api = RadarrSystemApis.updateLogFile(retrofit(server))
            val result = api.updateLogFiles()
            assertEquals(1, result.size)
            assertEquals("radarr.update.txt", result.single().filename)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/log/file/update",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateLogFileContentsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody("2026-10-01T00:00:00Z|Info|Update log line"))
            val api = RadarrSystemApis.updateLogFile(retrofit(server))
            val result = api.updateLogFileContents(filename = "radarr.update.txt")
            assertEquals("2026-10-01T00:00:00Z|Info|Update log line", result.string())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/log/file/update/radarr.update.txt",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun apiInfoErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.apiInfo(retrofit(server))
            try {
                api.apiInfo()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun authenticationErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            val api = RadarrSystemApis.authentication(retrofit(server))
            val result = api.login(
                username = "user".toRequestBody("text/plain".toMediaType()),
                password = "pass".toRequestBody("text/plain".toMediaType()),
            )
            assertEquals(401, result.code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun backupErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.backup(retrofit(server))
            assertEquals(503, api.restoreBackup(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun diskSpaceErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.diskSpace(retrofit(server))
            try {
                api.diskSpace()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun fileSystemErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.fileSystem(retrofit(server))
            try {
                api.fileSystem()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun healthErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.health(retrofit(server))
            try {
                api.health()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun localizationErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.localization(retrofit(server))
            try {
                api.localization()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun logErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.log(retrofit(server))
            try {
                api.log()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun logFileErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.logFile(retrofit(server))
            try {
                api.logFileContents(filename = "radarr.txt")
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun parseErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.parse(retrofit(server))
            try {
                api.parse(title = "Movie.Title.2026")
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun pingErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
            val api = RadarrSystemApis.ping(retrofit(server))
            assertEquals(500, api.ping().code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun rootFolderErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.rootFolder(retrofit(server))
            assertEquals(503, api.rootFolder(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun systemErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.system(retrofit(server))
            try {
                api.systemStatus()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun tagErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.tag(retrofit(server))
            assertEquals(503, api.tag(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun tagDetailsErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.tagDetails(retrofit(server))
            assertEquals(503, api.tagDetail(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun taskErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.task(retrofit(server))
            assertEquals(503, api.task(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.update(retrofit(server))
            try {
                api.updates()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateLogFileErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.updateLogFile(retrofit(server))
            try {
                api.updateLogFileContents(filename = "radarr.update.txt")
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }
}
