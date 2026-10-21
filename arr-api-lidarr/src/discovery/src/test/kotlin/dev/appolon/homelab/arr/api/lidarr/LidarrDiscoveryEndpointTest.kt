package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.SearchResource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun discoveryFixture(path: String): String = checkNotNull(
    LidarrDiscoveryEndpointTest::class.java.getResourceAsStream("/fixtures/lidarr/$path.json")
).bufferedReader().use { it.readText() }

internal inline fun <reified T> discoveryDto(path: String): T =
    ArrJson.decodeFromString(discoveryFixture(path))

internal fun discoveryRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/lidarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Each row is one spec operation under the discovery tag groups. */
data class DiscoveryEndpointCase(
    val name: String,
    val method: String,
    val path: String,
    val response: String = "",
    val queries: Map<String, List<String>> = emptyMap(),
    val body: JsonElement? = null,
    val call: suspend (Retrofit) -> Unit,
) {
    override fun toString(): String = name
}

@RunWith(Parameterized::class)
class LidarrDiscoveryEndpointTest(private val case: DiscoveryEndpointCase) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun happyPath() = runTest {
        server.enqueue(MockResponse().setBody(case.response))
        case.call(discoveryRetrofit(server))
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
            val lists = discoveryDto<List<ImportListResource>>("importlist/getImportLists")
            val list = discoveryDto<ImportListResource>("importlist/getImportListById")
            val schema = discoveryDto<List<ImportListResource>>("importlist/getImportListSchema")
            val exclusions =
                discoveryDto<List<ImportListExclusionResource>>(
                    "importlistexclusion/getImportListExclusions"
                )
            val filters = discoveryDto<List<CustomFilterResource>>("customfilter/getCustomFilters")
            val missing = discoveryDto<AlbumResourcePagingResource>("missing/getMissing")
            val cutoff = discoveryDto<AlbumResourcePagingResource>("cutoff/getCutoff")
            val search = discoveryDto<List<SearchResource>>("search/search")
            val bulk = ImportListBulkResource(
                ids = listOf(1, 2), tags = setOf(2), applyTags = ApplyTags.ADD,
                enableAutomaticAdd = false, rootFolderPath = "/music2", qualityProfileId = 3,
            )
            fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
                pairs.groupBy({ it.first }, { it.second })
            return listOf(
                DiscoveryEndpointCase(
                    "getImportLists", "GET", "api/v1/importlist",
                    discoveryFixture("importlist/getImportLists"),
                ) { r -> assertEquals(lists, LidarrDiscoveryApis.importList(r).getImportLists()) },
                DiscoveryEndpointCase(
                    "getImportList", "GET", "api/v1/importlist/1",
                    discoveryFixture("importlist/getImportListById"),
                ) { r -> assertEquals(list, LidarrDiscoveryApis.importList(r).getImportList(1)) },
                DiscoveryEndpointCase(
                    "getImportListSchema", "GET", "api/v1/importlist/schema",
                    discoveryFixture("importlist/getImportListSchema"),
                ) { r ->
                    assertEquals(schema, LidarrDiscoveryApis.importList(r).getImportListSchema())
                },
                DiscoveryEndpointCase(
                    "addImportList", "POST", "api/v1/importlist",
                    discoveryFixture("importlist/getImportListById"),
                    query("forceSave" to "true"),
                    ArrJson.encodeToJsonElement(list),
                ) { r ->
                    assertEquals(
                        list, LidarrDiscoveryApis.importList(r).addImportList(true, list)
                    )
                },
                DiscoveryEndpointCase(
                    "updateImportList", "PUT", "api/v1/importlist/1",
                    discoveryFixture("importlist/getImportListById"),
                    query("forceSave" to "false"),
                    ArrJson.encodeToJsonElement(list),
                ) { r ->
                    assertEquals(
                        list, LidarrDiscoveryApis.importList(r).updateImportList(1, false, list)
                    )
                },
                DiscoveryEndpointCase(
                    "updateImportLists", "PUT", "api/v1/importlist/bulk",
                    discoveryFixture("importlist/getImportListById"),
                    body = ArrJson.encodeToJsonElement(bulk),
                ) { r ->
                    assertEquals(list, LidarrDiscoveryApis.importList(r).updateImportLists(bulk))
                },
                DiscoveryEndpointCase(
                    "deleteImportLists", "DELETE", "api/v1/importlist/bulk",
                    body = ArrJson.encodeToJsonElement(bulk),
                ) { r -> LidarrDiscoveryApis.importList(r).deleteImportLists(bulk) },
                DiscoveryEndpointCase(
                    "deleteImportList", "DELETE", "api/v1/importlist/1",
                ) { r -> LidarrDiscoveryApis.importList(r).deleteImportList(1) },
                DiscoveryEndpointCase(
                    "testImportList", "POST", "api/v1/importlist/test",
                    queries = query("forceTest" to "true"),
                    body = ArrJson.encodeToJsonElement(list),
                ) { r -> LidarrDiscoveryApis.importList(r).testImportList(true, list) },
                DiscoveryEndpointCase(
                    "testAllImportLists", "POST", "api/v1/importlist/testall",
                ) { r -> LidarrDiscoveryApis.importList(r).testAllImportLists() },
                DiscoveryEndpointCase(
                    "importListAction", "POST", "api/v1/importlist/action/search",
                    body = ArrJson.encodeToJsonElement(list),
                ) { r -> LidarrDiscoveryApis.importList(r).importListAction("search", list) },
                DiscoveryEndpointCase(
                    "getImportListExclusions", "GET", "api/v1/importlistexclusion",
                    discoveryFixture("importlistexclusion/getImportListExclusions"),
                ) { r ->
                    assertEquals(
                        exclusions,
                        LidarrDiscoveryApis.importListExclusion(r).getImportListExclusions()
                    )
                },
                DiscoveryEndpointCase(
                    "getImportListExclusion", "GET", "api/v1/importlistexclusion/1",
                    ArrJson.encodeToString(exclusions.first()),
                ) { r ->
                    assertEquals(
                        exclusions.first(),
                        LidarrDiscoveryApis.importListExclusion(r).getImportListExclusion(1)
                    )
                },
                DiscoveryEndpointCase(
                    "addImportListExclusion", "POST", "api/v1/importlistexclusion",
                    ArrJson.encodeToString(exclusions.first()),
                    body = ArrJson.encodeToJsonElement(exclusions.first()),
                ) { r ->
                    assertEquals(
                        exclusions.first(),
                        LidarrDiscoveryApis.importListExclusion(r)
                            .addImportListExclusion(exclusions.first())
                    )
                },
                DiscoveryEndpointCase(
                    "updateImportListExclusion", "PUT", "api/v1/importlistexclusion/1",
                    ArrJson.encodeToString(exclusions.first()),
                    body = ArrJson.encodeToJsonElement(exclusions.first()),
                ) { r ->
                    assertEquals(
                        exclusions.first(),
                        LidarrDiscoveryApis.importListExclusion(r)
                            .updateImportListExclusion("1", exclusions.first())
                    )
                },
                DiscoveryEndpointCase(
                    "deleteImportListExclusion", "DELETE", "api/v1/importlistexclusion/1",
                ) { r ->
                    LidarrDiscoveryApis.importListExclusion(r).deleteImportListExclusion(1)
                },
                DiscoveryEndpointCase(
                    "getCustomFilters", "GET", "api/v1/customfilter",
                    discoveryFixture("customfilter/getCustomFilters"),
                ) { r ->
                    assertEquals(filters, LidarrDiscoveryApis.customFilter(r).getCustomFilters())
                },
                DiscoveryEndpointCase(
                    "getCustomFilter", "GET", "api/v1/customfilter/1",
                    ArrJson.encodeToString(filters.first()),
                ) { r ->
                    assertEquals(
                        filters.first(), LidarrDiscoveryApis.customFilter(r).getCustomFilter(1)
                    )
                },
                DiscoveryEndpointCase(
                    "addCustomFilter", "POST", "api/v1/customfilter",
                    ArrJson.encodeToString(filters.first()),
                    body = ArrJson.encodeToJsonElement(filters.first()),
                ) { r ->
                    assertEquals(
                        filters.first(), LidarrDiscoveryApis.customFilter(r)
                            .addCustomFilter(filters.first())
                    )
                },
                DiscoveryEndpointCase(
                    "updateCustomFilter", "PUT", "api/v1/customfilter/1",
                    ArrJson.encodeToString(filters.first()),
                    body = ArrJson.encodeToJsonElement(filters.first()),
                ) { r ->
                    assertEquals(
                        filters.first(),
                        LidarrDiscoveryApis.customFilter(r).updateCustomFilter("1", filters.first())
                    )
                },
                DiscoveryEndpointCase(
                    "deleteCustomFilter", "DELETE", "api/v1/customfilter/1",
                ) { r -> LidarrDiscoveryApis.customFilter(r).deleteCustomFilter(1) },
                DiscoveryEndpointCase(
                    "getMissing", "GET", "api/v1/wanted/missing",
                    discoveryFixture("missing/getMissing"),
                    query(
                        "page" to "1", "pageSize" to "10", "sortKey" to "releaseDate",
                        "sortDirection" to "descending", "includeArtist" to "true",
                        "monitored" to "true",
                    ),
                ) { r ->
                    assertEquals(
                        missing, LidarrDiscoveryApis.missing(r).getMissing(
                            1, 10, "releaseDate", SortDirection.DESCENDING, true, true,
                        )
                    )
                },
                DiscoveryEndpointCase(
                    "getMissingById", "GET", "api/v1/wanted/missing/3",
                    ArrJson.encodeToString(missing.records!!.first()),
                ) { r ->
                    assertEquals(
                        missing.records!!.first(),
                        LidarrDiscoveryApis.missing(r).getMissingById(3)
                    )
                },
                DiscoveryEndpointCase(
                    "getCutoff", "GET", "api/v1/wanted/cutoff",
                    discoveryFixture("cutoff/getCutoff"),
                    query(
                        "page" to "1", "pageSize" to "10", "sortKey" to "releaseDate",
                        "sortDirection" to "descending", "includeArtist" to "true",
                        "monitored" to "true",
                    ),
                ) { r ->
                    assertEquals(
                        cutoff, LidarrDiscoveryApis.cutoff(r).getCutoff(
                            1, 10, "releaseDate", SortDirection.DESCENDING, true, true,
                        )
                    )
                },
                DiscoveryEndpointCase(
                    "getCutoffById", "GET", "api/v1/wanted/cutoff/4",
                    ArrJson.encodeToString(cutoff.records!!.first()),
                ) { r ->
                    assertEquals(
                        cutoff.records!!.first(), LidarrDiscoveryApis.cutoff(r).getCutoffById(4)
                    )
                },
                DiscoveryEndpointCase(
                    "search", "GET", "api/v1/search",
                    discoveryFixture("search/search"),
                    query("term" to "Example"),
                ) { r ->
                    assertEquals(search, LidarrDiscoveryApis.search(r).search("Example"))
                },
                DiscoveryEndpointCase(
                    "getLoginPage", "GET", "login", "<html>login</html>",
                ) { r ->
                    assertEquals(
                        "<html>login</html>",
                        LidarrDiscoveryApis.staticResource(r).getLoginPage().body()!!.string()
                    )
                },
                DiscoveryEndpointCase(
                    "getContent", "GET", "content/main.css", "body{}",
                ) { r ->
                    assertEquals(
                        "body{}",
                        LidarrDiscoveryApis.staticResource(r).getContent("main.css").body()!!
                            .string()
                    )
                },
                DiscoveryEndpointCase(
                    "getRoot", "GET", "", "<html>lidarr</html>",
                ) { r ->
                    val response: Response<okhttp3.ResponseBody> =
                        LidarrDiscoveryApis.staticResource(r).getRoot()
                    assertTrue(response.isSuccessful)
                    assertEquals("<html>lidarr</html>", response.body()!!.string())
                },
                DiscoveryEndpointCase(
                    "getStaticResource", "GET", "favicon.ico", "icon",
                ) { r ->
                    assertEquals(
                        "icon",
                        LidarrDiscoveryApis.staticResource(r).getStaticResource("favicon.ico")
                            .body()!!.string()
                    )
                },
            ).map { case -> arrayOf<Any>(case) }
        }
    }
}
