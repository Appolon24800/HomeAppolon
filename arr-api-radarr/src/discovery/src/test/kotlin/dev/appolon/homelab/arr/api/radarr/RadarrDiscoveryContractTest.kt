package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.CollectionUpdateResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
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
class RadarrDiscoveryContractTest {
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use { it.readText() }

    @Test fun collectionsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("collection", "collections")))
            val api = RadarrImportlistApis.collection(retrofit(server))
            val result = api.collections(tmdbId = 1)
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/collection",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("tmdbId"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateCollectionsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("collection", "updateCollections")))
            val api = RadarrImportlistApis.collection(retrofit(server))
            val result = api.updateCollections(CollectionUpdateResource())
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/collection",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<CollectionUpdateResource>(
                request.body.readUtf8())
            assertEquals(CollectionUpdateResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateCollectionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("collection", "updateCollection")))
            val api = RadarrImportlistApis.collection(retrofit(server))
            val result = api.updateCollection(id = "1", CollectionResource())
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/collection/1",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<CollectionResource>(
                request.body.readUtf8())
            assertEquals(CollectionResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun collectionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("collection", "collection")))
            val api = RadarrImportlistApis.collection(retrofit(server))
            val result = api.collection(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/collection/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun customFiltersHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("customfilter", "customFilters")))
            val api = RadarrImportlistApis.customFilter(retrofit(server))
            val result = api.customFilters()
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/customfilter",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createCustomFilterHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("customfilter", "createCustomFilter")))
            val api = RadarrImportlistApis.customFilter(retrofit(server))
            val result = api.createCustomFilter(CustomFilterResource())
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/customfilter",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<CustomFilterResource>(
                request.body.readUtf8())
            assertEquals(CustomFilterResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateCustomFilterHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("customfilter", "updateCustomFilter")))
            val api = RadarrImportlistApis.customFilter(retrofit(server))
            val result = api.updateCustomFilter(id = "1", CustomFilterResource())
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/customfilter/1",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<CustomFilterResource>(
                request.body.readUtf8())
            assertEquals(CustomFilterResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeCustomFilterHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrImportlistApis.customFilter(retrofit(server))
            val result = api.removeCustomFilter(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/customfilter/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun customFilterHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("customfilter", "customFilter")))
            val api = RadarrImportlistApis.customFilter(retrofit(server))
            val result = api.customFilter(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/customfilter/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun cutoffHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("cutoff", "cutoff")))
            val api = RadarrImportlistApis.cutoff(retrofit(server))
            val result = api.cutoff(
                page = 1,
                pageSize = 1,
                sortKey = "string",
                sortDirection = SortDirection.DEFAULT,
                monitored = true,
            )
            assertEquals(1, result.totalRecords)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/wanted/cutoff",
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
                request.requestUrl!!.queryParameterValues("monitored"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "importLists")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.importLists()
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/importlist",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createImportListHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("importlist", "createImportList")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.createImportList(forceSave = true, ImportListResource())
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/importlist",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceSave"))
            val decoded = ArrJson.decodeFromString<ImportListResource>(
                request.body.readUtf8())
            assertEquals(ImportListResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateImportListHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("importlist", "updateImportList")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.updateImportList(id = 1, forceSave = true, ImportListResource())
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/importlist/1",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceSave"))
            val decoded = ArrJson.decodeFromString<ImportListResource>(
                request.body.readUtf8())
            assertEquals(ImportListResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeImportListHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "removeImportList")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.removeImportList(id = 1)
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/importlist/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "importList")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.importList(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/importlist/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateImportListBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "updateImportListBulk")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.updateImportListBulk(ImportListBulkResource())
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/importlist/bulk",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListBulkResource>(
                request.body.readUtf8())
            assertEquals(ImportListBulkResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeImportListBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "removeImportListBulk")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.removeImportListBulk(ImportListBulkResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/importlist/bulk",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListBulkResource>(
                request.body.readUtf8())
            assertEquals(ImportListBulkResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListSchemaHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "importListSchema")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.importListSchema()
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/importlist/schema",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun testImportListHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.testImportList(forceTest = true, ImportListResource())
            assertEquals(204, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/importlist/test",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceTest"))
            val decoded = ArrJson.decodeFromString<ImportListResource>(
                request.body.readUtf8())
            assertEquals(ImportListResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun testAllImportListsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlist", "testAllImportLists")))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.testAllImportLists()
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/importlist/testall",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListActionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrImportlistApis.importList(retrofit(server))
            val result = api.importListAction(name = "string", ImportListResource())
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/importlist/action/string",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListResource>(
                request.body.readUtf8())
            assertEquals(ImportListResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistconfig", "importListConfig")))
            val api = RadarrImportlistApis.importListConfig(retrofit(server))
            val result = api.importListConfig()
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/importlist",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateImportListConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("importlistconfig", "updateImportListConfig")))
            val api = RadarrImportlistApis.importListConfig(retrofit(server))
            val result = api.updateImportListConfig(id = "1", ImportListConfigResource())
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/importlist/1",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListConfigResource>(
                request.body.readUtf8())
            assertEquals(ImportListConfigResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistconfig", "importListConfigById")))
            val api = RadarrImportlistApis.importListConfig(retrofit(server))
            val result = api.importListConfigById(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/importlist/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun exclusionsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistexclusion", "exclusions")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.exclusions()
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/exclusions",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createExclusionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("importlistexclusion", "createExclusion")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.createExclusion(ImportListExclusionResource())
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/exclusions",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListExclusionResource>(
                request.body.readUtf8())
            assertEquals(ImportListExclusionResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun exclusionsPagedHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistexclusion", "exclusionsPaged")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.exclusionsPaged(
                page = 1,
                pageSize = 1,
                sortKey = "string",
                sortDirection = SortDirection.DEFAULT,
            )
            assertEquals(1, result.totalRecords)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/exclusions/paged",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("page"))
            assertEquals(listOf("1"),
                request.requestUrl!!.queryParameterValues("pageSize"))
            assertEquals(listOf("string"),
                request.requestUrl!!.queryParameterValues("sortKey"))
            assertEquals(listOf("default"),
                request.requestUrl!!.queryParameterValues("sortDirection"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateExclusionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("importlistexclusion", "updateExclusion")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.updateExclusion(id = "1", ImportListExclusionResource())
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/exclusions/1",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListExclusionResource>(
                request.body.readUtf8())
            assertEquals(ImportListExclusionResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeExclusionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.removeExclusion(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/exclusions/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun exclusionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistexclusion", "exclusion")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.exclusion(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/exclusions/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun addExclusionsBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistexclusion", "addExclusionsBulk")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.addExclusionsBulk(emptyList())
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/exclusions/bulk",
                request.requestUrl!!.encodedPath)
            assertEquals("[]", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeExclusionsBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistexclusion", "removeExclusionsBulk")))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            val result = api.removeExclusionsBulk(ImportListExclusionBulkResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/exclusions/bulk",
                request.requestUrl!!.encodedPath)
            val decoded = ArrJson.decodeFromString<ImportListExclusionBulkResource>(
                request.body.readUtf8())
            assertEquals(ImportListExclusionBulkResource(), decoded)
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListMoviesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistmovies", "importListMovies")))
            val api = RadarrImportlistApis.importListMovies(retrofit(server))
            val result = api.importListMovies(
                includeRecommendations = true,
                includeTrending = true,
                includePopular = true,
            )
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/importlist/movie",
                request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeRecommendations"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includeTrending"))
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("includePopular"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun addMoviesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("importlistmovies", "addMovies")))
            val api = RadarrImportlistApis.importListMovies(retrofit(server))
            val result = api.addMovies(emptyList())
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/importlist/movie",
                request.requestUrl!!.encodedPath)
            assertEquals("[]", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test fun missingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("missing", "missing")))
            val api = RadarrImportlistApis.missing(retrofit(server))
            val result = api.missing(
                page = 1,
                pageSize = 1,
                sortKey = "string",
                sortDirection = SortDirection.DEFAULT,
                monitored = true,
            )
            assertEquals(1, result.totalRecords)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/wanted/missing",
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
                request.requestUrl!!.queryParameterValues("monitored"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.importList(retrofit(server))
            try {
                api.importLists()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.importListConfig(retrofit(server))
            try {
                api.importListConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListExclusionErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.importListExclusion(retrofit(server))
            try {
                api.exclusions()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun importListMoviesErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.importListMovies(retrofit(server))
            try {
                api.importListMovies()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun collectionErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.collection(retrofit(server))
            try {
                api.collections()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun customFilterErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.customFilter(retrofit(server))
            try {
                api.customFilters()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun missingErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.missing(retrofit(server))
            try {
                api.missing()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun cutoffErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrImportlistApis.cutoff(retrofit(server))
            try {
                api.cutoff()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

}
