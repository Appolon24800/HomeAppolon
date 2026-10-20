package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.CommandResource
import dev.appolon.homelab.arr.api.prowlarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
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
 * Every operation under the six search-group tags runs one happy path and
 * one error path against MockWebServer. JSON fixtures are synthetic values
 * that validate against the bundled OpenAPI schemas; the spec ships no
 * examples. Newznab and download fixtures carry XML and raw bytes because
 * the spec declares no response content for those operations.
 */
@RunWith(Parameterized::class)
class ProwlarrSearchApiTest(private val case: SearchCase) {
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
                assertEquals(Unit, result.body())
            } else {
                val decode = requireNotNull(case.decode) { "plain calls need a decoder" }
                assertEquals(decode(fixture!!), result)
            }
            val request = server.takeRequest()
            assertEquals(case.method, request.method)
            val url = requireNotNull(request.requestUrl)
            assertEquals("/prefix/${case.path}", url.encodedPath)
            assertEquals(case.query.keys, url.queryParameterNames)
            case.query.forEach { (key, values) ->
                assertEquals(values, url.queryParameterValues(key))
            }
            if (case.expectedBody != null) {
                assertEquals(
                    ArrJson.parseToJsonElement(case.expectedBody),
                    ArrJson.parseToJsonElement(request.body.readUtf8()),
                )
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

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val release = ArrJson.decodeFromString<ReleaseResource>(
                fixtureText("search/response.json")
            )
            val releases = ArrJson.decodeFromString<List<ReleaseResource>>(
                fixtureText("search/list.json")
            )
            val command = ArrJson.decodeFromString<CommandResource>(
                fixtureText("command/response.json")
            )
            val customFilter = ArrJson.decodeFromString<CustomFilterResource>(
                fixtureText("custom-filter/response.json")
            )
            val releaseBody = ArrJson.encodeToString(release)
            val releasesBody = ArrJson.encodeToString(releases)
            val commandBody = ArrJson.encodeToString(command)
            val customFilterBody = ArrJson.encodeToString(customFilter)
            val cases = mutableListOf<SearchCase>()

            fun add(
                method: String,
                path: String,
                query: Map<String, List<String>> = emptyMap(),
                fixture: String? = null,
                decode: ((String) -> Any?)? = null,
                expectedBody: String? = null,
                call: suspend (Retrofit) -> Any?,
            ) {
                cases += SearchCase(method, path, query, fixture, decode, expectedBody, call)
            }

            add(
                "GET", "api/v1/search",
                query = linkedMapOf(
                    "query" to listOf("Spec Release"),
                    "type" to listOf("search"),
                    "indexerIds" to listOf("1", "2"),
                    "categories" to listOf("2000", "2040"),
                    "limit" to listOf("100"),
                    "offset" to listOf("0"),
                ),
                fixture = "search/list.json",
                decode = { text -> ArrJson.decodeFromString<List<ReleaseResource>>(text) },
            ) {
                ProwlarrSearchApis.search(it).getSearch(
                    "Spec Release", "search", listOf(1, 2), listOf(2000, 2040), 100, 0
                )
            }
            add(
                "POST", "api/v1/search",
                fixture = "search/response.json",
                decode = { text -> ArrJson.decodeFromString<ReleaseResource>(text) },
                expectedBody = releaseBody,
            ) {
                ProwlarrSearchApis.search(it).createSearch(release)
            }
            add(
                "POST", "api/v1/search/bulk",
                fixture = "search/response.json",
                decode = { text -> ArrJson.decodeFromString<ReleaseResource>(text) },
                expectedBody = releasesBody,
            ) {
                ProwlarrSearchApis.search(it).createSearchBulk(releases)
            }

            // Representative newznab parameters; the rest ride the same @Query path.
            val newznabQuery = linkedMapOf(
                "t" to listOf("tvsearch"),
                "q" to listOf("Spec Release"),
                "cat" to listOf("2000"),
                "imdbid" to listOf("1375666"),
                "tmdbid" to listOf("603"),
                "extended" to listOf("1"),
                "limit" to listOf("100"),
                "offset" to listOf("0"),
                "minsize" to listOf("1048576"),
                "maxsize" to listOf("1073741824"),
                "season" to listOf("1"),
                "ep" to listOf("01"),
                "year" to listOf("2026"),
            )
            val newznabXml = { text: String -> text }
            add(
                "GET", "api/v1/indexer/2/newznab",
                query = newznabQuery,
                fixture = "newznab/response.xml",
                decode = newznabXml,
            ) {
                ProwlarrSearchApis.newznab(it).getNewznab(
                    2, "tvsearch", "Spec Release", "2000", "1375666", 603, "1",
                    100, 0, null, null, 1048576L, 1073741824L,
                    null, null, null, null, null, 1, "01",
                    null, null, null, null, 2026, null, null, null, null,
                    null, null, null, null
                ).string()
            }
            add(
                "GET", "2/api",
                query = newznabQuery,
                fixture = "newznab/response.xml",
                decode = newznabXml,
            ) {
                ProwlarrSearchApis.newznab(it).getNewznabLegacy(
                    2, "tvsearch", "Spec Release", "2000", "1375666", 603, "1",
                    100, 0, null, null, 1048576L, 1073741824L,
                    null, null, null, null, null, 1, "01",
                    null, null, null, null, 2026, null, null, null, null,
                    null, null, null, null
                ).string()
            }
            val downloadBytes = { text: String -> text }
            add(
                "GET", "api/v1/indexer/2/download",
                query = linkedMapOf(
                    "link" to listOf("https://example.org/download/1"),
                    "file" to listOf("Spec.Release.1080p.mkv"),
                ),
                fixture = "newznab/download.bin",
                decode = downloadBytes,
            ) {
                ProwlarrSearchApis.newznab(it)
                    .getDownload(2, "https://example.org/download/1", "Spec.Release.1080p.mkv")
                    .string()
            }
            add(
                "GET", "2/download",
                query = linkedMapOf(
                    "link" to listOf("https://example.org/download/1"),
                    "file" to listOf("Spec.Release.1080p.mkv"),
                ),
                fixture = "newznab/download.bin",
                decode = downloadBytes,
            ) {
                ProwlarrSearchApis.newznab(it)
                    .getDownloadLegacy(2, "https://example.org/download/1", "Spec.Release.1080p.mkv")
                    .string()
            }

            add(
                "GET", "api/v1/history",
                query = linkedMapOf(
                    "page" to listOf("2"),
                    "pageSize" to listOf("5"),
                    "sortKey" to listOf("date"),
                    "sortDirection" to listOf("descending"),
                    "eventType" to listOf("1", "2"),
                    "successful" to listOf("true"),
                    "downloadId" to listOf("spec-download-1"),
                    "indexerIds" to listOf("1"),
                ),
                fixture = "history/paged.json",
                decode = { text -> ArrJson.decodeFromString<HistoryResourcePagingResource>(text) },
            ) {
                ProwlarrSearchApis.history(it).getHistory(
                    2, 5, "date", SortDirection.DESCENDING, listOf(1, 2),
                    true, "spec-download-1", listOf(1)
                )
            }
            add(
                "GET", "api/v1/history/since",
                query = linkedMapOf(
                    "date" to listOf("2026-01-15T00:00:00Z"),
                    "eventType" to listOf("indexerQuery"),
                ),
                fixture = "history/list.json",
                decode = { text -> ArrJson.decodeFromString<List<HistoryResource>>(text) },
            ) {
                ProwlarrSearchApis.history(it)
                    .getHistorySince("2026-01-15T00:00:00Z", HistoryEventType.INDEXER_QUERY)
            }
            add(
                "GET", "api/v1/history/indexer",
                query = linkedMapOf(
                    "indexerId" to listOf("1"),
                    "eventType" to listOf("releaseGrabbed"),
                    "limit" to listOf("10"),
                ),
                fixture = "history/list.json",
                decode = { text -> ArrJson.decodeFromString<List<HistoryResource>>(text) },
            ) {
                ProwlarrSearchApis.history(it)
                    .getHistoryForIndexer(1, HistoryEventType.RELEASE_GRABBED, 10)
            }

            add(
                "GET", "api/v1/indexerstats",
                query = linkedMapOf(
                    "startDate" to listOf("2026-01-01T00:00:00Z"),
                    "endDate" to listOf("2026-01-31T23:59:59Z"),
                    "indexers" to listOf("1,2"),
                    "protocols" to listOf("torrent"),
                    "tags" to listOf("spec"),
                ),
                fixture = "indexer-stats/response.json",
                decode = { text -> ArrJson.decodeFromString<IndexerStatsResource>(text) },
            ) {
                ProwlarrSearchApis.indexerStats(it).getIndexerStats(
                    "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", "1,2", "torrent", "spec"
                )
            }

            val customFilterList: (String) -> Any? = {
                ArrJson.decodeFromString<List<CustomFilterResource>>(it)
            }
            val oneCustomFilter: (String) -> Any? = {
                ArrJson.decodeFromString<CustomFilterResource>(it)
            }
            add(
                "GET", "api/v1/customfilter",
                fixture = "custom-filter/list.json", decode = customFilterList,
            ) {
                ProwlarrSearchApis.customFilter(it).getCustomFilters()
            }
            add(
                "GET", "api/v1/customfilter/1",
                fixture = "custom-filter/response.json", decode = oneCustomFilter,
            ) {
                ProwlarrSearchApis.customFilter(it).getCustomFilter(1)
            }
            add(
                "POST", "api/v1/customfilter",
                fixture = "custom-filter/response.json", decode = oneCustomFilter,
                expectedBody = customFilterBody,
            ) {
                ProwlarrSearchApis.customFilter(it).createCustomFilter(customFilter)
            }
            add(
                "PUT", "api/v1/customfilter/1",
                fixture = "custom-filter/response.json", decode = oneCustomFilter,
                expectedBody = customFilterBody,
            ) {
                ProwlarrSearchApis.customFilter(it).updateCustomFilter("1", customFilter)
            }
            add("DELETE", "api/v1/customfilter/1") {
                ProwlarrSearchApis.customFilter(it).deleteCustomFilter(1)
            }

            val commandList: (String) -> Any? = {
                ArrJson.decodeFromString<List<CommandResource>>(it)
            }
            val oneCommand: (String) -> Any? = {
                ArrJson.decodeFromString<CommandResource>(it)
            }
            add(
                "GET", "api/v1/command",
                fixture = "command/list.json", decode = commandList,
            ) {
                ProwlarrSearchApis.command(it).getCommands()
            }
            add(
                "GET", "api/v1/command/1",
                fixture = "command/response.json", decode = oneCommand,
            ) {
                ProwlarrSearchApis.command(it).getCommand(1)
            }
            add(
                "POST", "api/v1/command",
                fixture = "command/response.json", decode = oneCommand,
                expectedBody = commandBody,
            ) {
                ProwlarrSearchApis.command(it).createCommand(command)
            }
            add("DELETE", "api/v1/command/1") {
                ProwlarrSearchApis.command(it).deleteCommand(1)
            }
            return cases.map { arrayOf(it) }
        }
    }
}

/**
 * One operation's wire expectations: method, path, query values, response
 * fixture with its decoder, and the request body the operation must send.
 */
data class SearchCase(
    val method: String,
    val path: String,
    val query: Map<String, List<String>> = emptyMap(),
    val fixture: String? = null,
    val decode: ((String) -> Any?)? = null,
    val expectedBody: String? = null,
    val call: suspend (Retrofit) -> Any?,
) {
    override fun toString() = "$method $path"
}
