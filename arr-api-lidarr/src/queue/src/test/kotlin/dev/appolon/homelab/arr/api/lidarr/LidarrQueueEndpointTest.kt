package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.CommandResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.api.lidarr.model.ParseResource
import dev.appolon.homelab.arr.api.lidarr.model.PingResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun queueFixture(path: String): String = checkNotNull(
    LidarrQueueEndpointTest::class.java.getResourceAsStream("/fixtures/lidarr/$path.json")
).bufferedReader().use { it.readText() }

internal inline fun <reified T> queueDto(path: String): T =
    ArrJson.decodeFromString(queueFixture(path))

internal fun queueRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/lidarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Each row is one spec operation, including the HEAD omitted from the tag inventory. */
data class QueueEndpointCase(
    val name: String,
    val method: String,
    val path: String,
    val response: String = "",
    val queries: Map<String, List<String>> = emptyMap(),
    val body: JsonElement? = null,
    val call: suspend (Retrofit) -> Any?,
) {
    override fun toString(): String = name
}

@RunWith(Parameterized::class)
class LidarrQueueEndpointTest(private val case: QueueEndpointCase) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun happyPath() = runTest {
        server.enqueue(MockResponse().setBody(case.response))
        case.call(queueRetrofit(server))
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
        } else {
            assertEquals(0L, request.bodySize)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun operations(): List<Array<Any>> {
            val queue = queueDto<QueueResourcePagingResource>("queue/getQueue")
            val details = queueDto<List<QueueResource>>("queuedetails/getQueueDetails")
            val status = queueDto<QueueStatusResource>("queuestatus/getQueueStatus")
            val blocklist = queueDto<BlocklistResourcePagingResource>("blocklist/getBlocklist")
            val history = queueDto<HistoryResourcePagingResource>("history/getHistory")
            val command = queueDto<CommandResource>("command/command")
            val imports = queueDto<List<ManualImportResource>>("manualimport/getManualImport")
            val release = queueDto<ReleaseResource>("release/release")
            val pushed = queueDto<ReleaseResource>("releasepush/pushRelease")
            val parsed = queueDto<ParseResource>("parse/parse")
            val ping = queueDto<PingResource>("ping/ping")
            val bulk = queueDto<QueueBulkResource>("queueaction/grabQueueBulk")
            val blockBulk = BlocklistBulkResource(listOf(1, 2))
            val updates = listOf(ManualImportUpdateResource(
                id = 1, artistId = 2, albumId = 3, trackIds = listOf(5),
                replaceExistingFiles = true,
            ))
            fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
                pairs.groupBy({ it.first }, { it.second })
            return listOf(
                QueueEndpointCase(
                    "getQueue", "GET", "api/v1/queue", queueFixture("queue/getQueue"),
                    query(
                        "page" to "1", "pageSize" to "10", "sortKey" to "title",
                        "sortDirection" to "ascending", "includeUnknownArtistItems" to "true",
                        "includeArtist" to "true", "includeAlbum" to "true",
                        "artistIds" to "2", "artistIds" to "4", "protocol" to "torrent",
                        "quality" to "1", "quality" to "2",
                    ),
                ) { r ->
                    assertEquals(queue, LidarrQueueApis.queue(r).getQueue(
                        1, 10, "title", SortDirection.ASCENDING, true, true, true,
                        listOf(2, 4), DownloadProtocol.TORRENT, listOf(1, 2),
                    ))
                },
                QueueEndpointCase(
                    "deleteQueue", "DELETE", "api/v1/queue/1",
                    queries = query(
                        "removeFromClient" to "false", "blocklist" to "true",
                        "skipRedownload" to "true", "changeCategory" to "true",
                    ),
                ) { r -> LidarrQueueApis.queue(r).deleteQueue(1, false, true, true, true) },
                QueueEndpointCase(
                    "deleteQueueBulk", "DELETE", "api/v1/queue/bulk",
                    queries = query(
                        "removeFromClient" to "true", "blocklist" to "false",
                        "skipRedownload" to "false", "changeCategory" to "false",
                    ),
                    body = ArrJson.encodeToJsonElement(bulk),
                ) { r -> LidarrQueueApis.queue(r).deleteQueueBulk(bulk, true, false, false, false) },
                QueueEndpointCase("grabQueue", "POST", "api/v1/queue/grab/1") { r ->
                    LidarrQueueApis.queueAction(r).grabQueue(1)
                },
                QueueEndpointCase(
                    "grabQueueBulk", "POST", "api/v1/queue/grab/bulk",
                    body = ArrJson.encodeToJsonElement(bulk),
                ) { r -> LidarrQueueApis.queueAction(r).grabQueueBulk(bulk) },
                QueueEndpointCase(
                    "getQueueDetails", "GET", "api/v1/queue/details",
                    queueFixture("queuedetails/getQueueDetails"),
                    query(
                        "artistId" to "2", "albumIds" to "3", "albumIds" to "5",
                        "includeArtist" to "false", "includeAlbum" to "true",
                    ),
                ) { r ->
                    assertEquals(details, LidarrQueueApis.queueDetails(r).getQueueDetails(
                        2, listOf(3, 5), false, true,
                    ))
                },
                QueueEndpointCase(
                    "getQueueStatus", "GET", "api/v1/queue/status",
                    queueFixture("queuestatus/getQueueStatus"),
                ) { r -> assertEquals(status, LidarrQueueApis.queueStatus(r).getQueueStatus()) },
                QueueEndpointCase(
                    "getBlocklist", "GET", "api/v1/blocklist",
                    queueFixture("blocklist/getBlocklist"),
                    query("page" to "1", "pageSize" to "10", "sortKey" to "date",
                        "sortDirection" to "descending"),
                ) { r ->
                    assertEquals(blocklist, LidarrQueueApis.blocklist(r).getBlocklist(
                        1, 10, "date", SortDirection.DESCENDING,
                    ))
                },
                QueueEndpointCase("deleteBlocklist", "DELETE", "api/v1/blocklist/1") { r ->
                    LidarrQueueApis.blocklist(r).deleteBlocklist(1)
                },
                QueueEndpointCase(
                    "deleteBlocklistBulk", "DELETE", "api/v1/blocklist/bulk",
                    body = ArrJson.encodeToJsonElement(blockBulk),
                ) { r -> LidarrQueueApis.blocklist(r).deleteBlocklistBulk(blockBulk) },
                QueueEndpointCase(
                    "getHistory", "GET", "api/v1/history", queueFixture("history/getHistory"),
                    query(
                        "page" to "1", "pageSize" to "10", "sortKey" to "date",
                        "sortDirection" to "descending", "includeArtist" to "true",
                        "includeAlbum" to "false", "includeTrack" to "true",
                        "eventType" to "1", "eventType" to "3", "albumId" to "3",
                        "downloadId" to "string", "artistIds" to "2", "artistIds" to "4",
                        "quality" to "1", "quality" to "2",
                    ),
                ) { r ->
                    assertEquals(history, LidarrQueueApis.history(r).getHistory(
                        1, 10, "date", SortDirection.DESCENDING, true, false, true,
                        listOf(1, 3), 3, "string", listOf(2, 4), listOf(1, 2),
                    ))
                },
                QueueEndpointCase(
                    "getHistorySince", "GET", "api/v1/history/since",
                    ArrJson.encodeToString(history.records),
                    query(
                        "date" to "2026-01-01T00:00:00+01:00",
                        "eventType" to "trackFileImported", "includeArtist" to "true",
                        "includeAlbum" to "true", "includeTrack" to "false",
                    ),
                ) { r ->
                    assertEquals(history.records, LidarrQueueApis.history(r).getHistorySince(
                        "2026-01-01T00:00:00+01:00", EntityHistoryEventType.TRACK_FILE_IMPORTED,
                        true, true, false,
                    ))
                },
                QueueEndpointCase(
                    "getArtistHistory", "GET", "api/v1/history/artist",
                    ArrJson.encodeToString(history.records),
                    query(
                        "artistId" to "2", "albumId" to "3", "eventType" to "downloadFailed",
                        "includeArtist" to "false", "includeAlbum" to "false",
                        "includeTrack" to "true",
                    ),
                ) { r ->
                    assertEquals(history.records, LidarrQueueApis.history(r).getArtistHistory(
                        2, 3, EntityHistoryEventType.DOWNLOAD_FAILED, false, false, true,
                    ))
                },
                QueueEndpointCase("failHistory", "POST", "api/v1/history/failed/1") { r ->
                    LidarrQueueApis.history(r).failHistory(1)
                },
                QueueEndpointCase(
                    "getCommand", "GET", "api/v1/command/1", queueFixture("command/command"),
                ) { r -> assertEquals(command, LidarrQueueApis.command(r).getCommand(1)) },
                QueueEndpointCase("deleteCommand", "DELETE", "api/v1/command/1") { r ->
                    LidarrQueueApis.command(r).deleteCommand(1)
                },
                QueueEndpointCase(
                    "addCommand", "POST", "api/v1/command", queueFixture("command/command"),
                    body = ArrJson.encodeToJsonElement(command),
                ) { r -> assertEquals(command, LidarrQueueApis.command(r).addCommand(command)) },
                QueueEndpointCase(
                    "getCommands", "GET", "api/v1/command",
                    "[${queueFixture("command/command")}]",
                ) { r -> assertEquals(listOf(command), LidarrQueueApis.command(r).getCommands()) },
                QueueEndpointCase(
                    "getManualImport", "GET", "api/v1/manualimport",
                    queueFixture("manualimport/getManualImport"),
                    query(
                        "folder" to "/music/incoming", "downloadId" to "string",
                        "artistId" to "2", "filterExistingFiles" to "false",
                        "replaceExistingFiles" to "true",
                    ),
                ) { r ->
                    assertEquals(imports, LidarrQueueApis.manualImport(r).getManualImport(
                        "/music/incoming", "string", 2, false, true,
                    ))
                },
                QueueEndpointCase(
                    "updateManualImport", "POST", "api/v1/manualimport",
                    body = ArrJson.encodeToJsonElement(updates),
                ) { r -> LidarrQueueApis.manualImport(r).updateManualImport(updates) },
                QueueEndpointCase(
                    "getReleases", "GET", "api/v1/release", "[${queueFixture("release/release")}]",
                    query("albumId" to "3", "artistId" to "2"),
                ) { r ->
                    assertEquals(listOf(release), LidarrQueueApis.release(r).getReleases(3, 2))
                },
                QueueEndpointCase(
                    "grabRelease", "POST", "api/v1/release", queueFixture("release/release"),
                    body = ArrJson.encodeToJsonElement(release),
                ) { r -> assertEquals(release, LidarrQueueApis.release(r).grabRelease(release)) },
                QueueEndpointCase(
                    "pushRelease", "POST", "api/v1/release/push",
                    queueFixture("releasepush/pushRelease"), body = ArrJson.encodeToJsonElement(pushed),
                ) { r -> assertEquals(pushed, LidarrQueueApis.releasePush(r).pushRelease(pushed)) },
                QueueEndpointCase(
                    "parse", "GET", "api/v1/parse", queueFixture("parse/parse"),
                    query("title" to "Artist + Album & edition"),
                ) { r ->
                    assertEquals(parsed, LidarrQueueApis.parse(r).parse("Artist + Album & edition"))
                },
                QueueEndpointCase("ping", "GET", "ping", queueFixture("ping/ping")) { r ->
                    assertEquals(ping, LidarrQueueApis.ping(r).ping())
                },
                QueueEndpointCase("headPing", "HEAD", "ping") { r ->
                    val response = LidarrQueueApis.ping(r).headPing()
                    assertEquals(200, response.code())
                    assertNull(response.body())
                },
            ).map { arrayOf(it) }
        }
    }
}
