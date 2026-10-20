package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each spec operation has its own fixture and verifies the base-path prefix. */
class RadarrQueueContractTest {
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use { it.readText() }

    @Test fun blocklistHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("blocklist", "blocklist")))
            val api = RadarrQueueApis.blocklist(retrofit(server))
            val result = api.blocklist(
                page = 1,
                pageSize = 1,
                sortKey = "string",
                sortDirection = SortDirection.DEFAULT,
                movieIds = listOf(1, 2),
                protocols = listOf(DownloadProtocol.TORRENT),
            )
            assertEquals(1, result.totalRecords)
            assertEquals(1, result.records!!.single().id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/blocklist",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("page"))
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("pageSize"))
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("sortKey"))
            assertEquals(listOf("default"),
                request.requestUrl!!.queryParameterValues("sortDirection"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("movieIds"))
            assertEquals(listOf("torrent"),
                request.requestUrl!!.queryParameterValues("protocols"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun movieBlocklistHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("blocklist", "movieBlocklist")))
            val api = RadarrQueueApis.blocklist(retrofit(server))
            val result = api.movieBlocklist(
                movieId = 1,
            )
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/blocklist/movie",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("movieId"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeBlocklistHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrQueueApis.blocklist(retrofit(server))
            val result = api.removeBlocklist(
                id = 1,
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/blocklist/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeBlocklistBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("blocklist", "removeBlocklistBulk")))
            val api = RadarrQueueApis.blocklist(retrofit(server))
            val result = api.removeBlocklistBulk(
                BlocklistBulkResource(),
            )
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/blocklist/bulk",
                request.requestUrl!!.encodedPath)
            assertEquals(BlocklistBulkResource(), ArrJson.decodeFromString<BlocklistBulkResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun startCommandHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("command", "startCommand")))
            val api = RadarrQueueApis.command(retrofit(server))
            val result = api.startCommand(
                CommandResource(),
            )
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/command",
                request.requestUrl!!.encodedPath)
            assertEquals(CommandResource(), ArrJson.decodeFromString<CommandResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun commandsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("command", "commands")))
            val api = RadarrQueueApis.command(retrofit(server))
            val result = api.commands()
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/command",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun cancelCommandHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrQueueApis.command(retrofit(server))
            val result = api.cancelCommand(
                id = 1,
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/command/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun commandHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("command", "command")))
            val api = RadarrQueueApis.command(retrofit(server))
            val result = api.command(
                id = 1,
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/command/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun historyHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("history", "history")))
            val api = RadarrQueueApis.history(retrofit(server))
            val result = api.history(
                page = 1,
                pageSize = 1,
                sortKey = "string",
                sortDirection = SortDirection.DEFAULT,
                includeMovie = true,
                eventType = listOf(1, 2),
                downloadId = "string",
                movieIds = listOf(1, 2),
                languages = listOf(1, 2),
                quality = listOf(1, 2),
            )
            assertEquals(1, result.totalRecords)
            assertEquals(1, result.records!!.single().id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/history",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("page"))
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("pageSize"))
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("sortKey"))
            assertEquals(listOf("default"),
                request.requestUrl!!.queryParameterValues("sortDirection"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeMovie"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("eventType"))
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("downloadId"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("movieIds"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("languages"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("quality"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun historySinceHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("history", "historySince")))
            val api = RadarrQueueApis.history(retrofit(server))
            val result = api.historySince(
                date = "string",
                eventType = MovieHistoryEventType.UNKNOWN,
                includeMovie = true,
            )
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/history/since",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("date"))
            assertEquals(listOf("unknown"),
                request.requestUrl!!.queryParameterValues("eventType"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeMovie"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun movieHistoryHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("history", "movieHistory")))
            val api = RadarrQueueApis.history(retrofit(server))
            val result = api.movieHistory(
                movieId = 1,
                eventType = MovieHistoryEventType.UNKNOWN,
                includeMovie = true,
            )
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/history/movie",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("movieId"))
            assertEquals(listOf("unknown"),
                request.requestUrl!!.queryParameterValues("eventType"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeMovie"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun markHistoryFailedHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("history", "markHistoryFailed")))
            val api = RadarrQueueApis.history(retrofit(server))
            val result = api.markHistoryFailed(
                id = 1,
            )
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/history/failed/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun manualImportHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("manualimport", "manualImport")))
            val api = RadarrQueueApis.manualImport(retrofit(server))
            val result = api.manualImport(
                folder = "string",
                downloadId = "string",
                movieId = 1,
                filterExistingFiles = true,
            )
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/manualimport",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("folder"))
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("downloadId"))
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("movieId"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("filterExistingFiles"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun reprocessManualImportHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("manualimport", "reprocessManualImport")))
            val api = RadarrQueueApis.manualImport(retrofit(server))
            val result = api.reprocessManualImport(
                emptyList(),
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/manualimport",
                request.requestUrl!!.encodedPath)
            assertEquals("[]", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeQueueHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrQueueApis.queue(retrofit(server))
            val result = api.removeQueue(
                id = 1,
                removeFromClient = true,
                blocklist = true,
                skipRedownload = true,
                changeCategory = true,
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/queue/1",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("removeFromClient"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("blocklist"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("skipRedownload"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("changeCategory"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeQueueBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("queue", "removeQueueBulk")))
            val api = RadarrQueueApis.queue(retrofit(server))
            val result = api.removeQueueBulk(
                removeFromClient = true,
                blocklist = true,
                skipRedownload = true,
                changeCategory = true,
                QueueBulkResource(),
            )
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/queue/bulk",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("removeFromClient"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("blocklist"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("skipRedownload"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("changeCategory"))
            assertEquals(QueueBulkResource(), ArrJson.decodeFromString<QueueBulkResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("queue", "queue")))
            val api = RadarrQueueApis.queue(retrofit(server))
            val result = api.queue(
                page = 1,
                pageSize = 1,
                sortKey = "string",
                sortDirection = SortDirection.DEFAULT,
                includeUnknownMovieItems = true,
                includeMovie = true,
                movieIds = listOf(1, 2),
                protocol = DownloadProtocol.UNKNOWN,
                languages = listOf(1, 2),
                quality = listOf(1, 2),
                status = listOf(QueueStatus.QUEUED),
            )
            assertEquals(1, result.totalRecords)
            assertEquals(1, result.records!!.single().id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/queue",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("page"))
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("pageSize"))
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("sortKey"))
            assertEquals(listOf("default"),
                request.requestUrl!!.queryParameterValues("sortDirection"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeUnknownMovieItems"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeMovie"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("movieIds"))
            assertEquals(listOf("unknown"),
                request.requestUrl!!.queryParameterValues("protocol"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("languages"))
            assertEquals(listOf("1", "2"),
                request.requestUrl!!.queryParameterValues("quality"))
            assertEquals(listOf("queued"),
                request.requestUrl!!.queryParameterValues("status"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun grabQueueHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("queueaction", "grabQueue")))
            val api = RadarrQueueApis.queueAction(retrofit(server))
            val result = api.grabQueue(
                id = 1,
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/queue/grab/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun grabQueueBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("queueaction", "grabQueueBulk")))
            val api = RadarrQueueApis.queueAction(retrofit(server))
            val result = api.grabQueueBulk(
                QueueBulkResource(),
            )
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/queue/grab/bulk",
                request.requestUrl!!.encodedPath)
            assertEquals(QueueBulkResource(), ArrJson.decodeFromString<QueueBulkResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueDetailsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("queuedetails", "queueDetails")))
            val api = RadarrQueueApis.queueDetails(retrofit(server))
            val result = api.queueDetails(
                movieId = 1,
                includeMovie = true,
            )
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/queue/details",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("movieId"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeMovie"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueStatusHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("queuestatus", "queueStatus")))
            val api = RadarrQueueApis.queueStatus(retrofit(server))
            val result = api.queueStatus()
            assertEquals(1, result.totalCount)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/queue/status",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.queue(retrofit(server))
            assertEquals(503, api.removeQueue(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueActionErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.queueAction(retrofit(server))
            assertEquals(503, api.grabQueue(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueDetailsErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.queueDetails(retrofit(server))
            try {
                api.queueDetails()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun queueStatusErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.queueStatus(retrofit(server))
            try {
                api.queueStatus()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun blocklistErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.blocklist(retrofit(server))
            try {
                api.blocklist()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun historyErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.history(retrofit(server))
            try {
                api.history()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun manualImportErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.manualImport(retrofit(server))
            try {
                api.manualImport()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun commandErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrQueueApis.command(retrofit(server))
            assertEquals(503, api.startCommand(body = CommandResource()).code())
        } finally {
            server.shutdown()
        }
    }

}
