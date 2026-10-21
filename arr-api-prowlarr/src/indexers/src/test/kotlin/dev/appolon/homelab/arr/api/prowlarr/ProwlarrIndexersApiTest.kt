package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
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
 * Every operation under the eight indexer-group tags runs one happy path and
 * one error path against MockWebServer. Fixtures are synthetic JSON that
 * validates against the bundled OpenAPI schemas; the spec ships no examples.
 */
@RunWith(Parameterized::class)
class ProwlarrIndexersApiTest(private val case: IndexerCase) {
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
                if (case.decode != null) {
                    assertEquals(case.decode.invoke(fixture!!), result.body())
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
            val indexer = ArrJson.decodeFromString<IndexerResource>(
                fixtureText("indexer/response.json")
            )
            val indexerBulk = IndexerBulkResource(
                ids = listOf(1, 2),
                tags = listOf(3),
                applyTags = ApplyTags.ADD,
                enable = true,
                appProfileId = 1,
                priority = 25,
            )
            val proxy = ArrJson.decodeFromString<IndexerProxyResource>(
                fixtureText("indexer-proxy/response.json")
            )
            val profile = ArrJson.decodeFromString<AppProfileResource>(
                fixtureText("app-profile/response.json")
            )
            val application = ArrJson.decodeFromString<ApplicationResource>(
                fixtureText("application/response.json")
            )
            val applicationBulk = ApplicationBulkResource(
                ids = listOf(1, 2),
                tags = listOf(3),
                applyTags = ApplyTags.REPLACE,
                syncLevel = ApplicationSyncLevel.FULL_SYNC,
            )
            val tag = TagResource(id = 1, label = "spec-tag")
            val indexerBody = ArrJson.encodeToString(indexer)
            val indexerBulkBody = ArrJson.encodeToString(indexerBulk)
            val proxyBody = ArrJson.encodeToString(proxy)
            val profileBody = ArrJson.encodeToString(profile)
            val applicationBody = ArrJson.encodeToString(application)
            val applicationBulkBody = ArrJson.encodeToString(applicationBulk)
            val tagBody = ArrJson.encodeToString(tag)
            val forceSave = mapOf("forceSave" to listOf("true"))
            val forceTest = mapOf("forceTest" to listOf("true"))
            val cases = mutableListOf<IndexerCase>()

            fun add(
                method: String,
                path: String,
                query: Map<String, List<String>> = emptyMap(),
                fixture: String? = null,
                decode: ((String) -> Any?)? = null,
                expectedBody: String? = null,
                call: suspend (Retrofit) -> Any?,
            ) {
                cases += IndexerCase(
                    method, path, query, fixture, decode, expectedBody, call
                )
            }

            val indexerList: (String) -> Any? = {
                ArrJson.decodeFromString<List<IndexerResource>>(it)
            }
            val oneIndexer: (String) -> Any? = {
                ArrJson.decodeFromString<IndexerResource>(it)
            }
            add("GET", "api/v1/indexer", fixture = "indexer/list.json", decode = indexerList) {
                ProwlarrIndexersApis.indexer(it).getIndexers()
            }
            add(
                "GET", "api/v1/indexer/1",
                fixture = "indexer/response.json", decode = oneIndexer,
            ) {
                ProwlarrIndexersApis.indexer(it).getIndexer(1)
            }
            add(
                "GET", "api/v1/indexer/schema",
                fixture = "indexer/schema.json", decode = indexerList,
            ) {
                ProwlarrIndexersApis.indexer(it).getIndexerSchemas()
            }
            add(
                "POST", "api/v1/indexer", query = forceSave,
                fixture = "indexer/response.json", decode = oneIndexer,
                expectedBody = indexerBody,
            ) {
                ProwlarrIndexersApis.indexer(it).createIndexer(true, indexer)
            }
            add(
                "PUT", "api/v1/indexer/1",
                fixture = "indexer/response.json", decode = oneIndexer,
                expectedBody = indexerBody,
            ) {
                ProwlarrIndexersApis.indexer(it).updateIndexer("1", null, indexer)
            }
            add(
                "PUT", "api/v1/indexer/bulk",
                fixture = "indexer/response.json", decode = oneIndexer,
                expectedBody = indexerBulkBody,
            ) {
                ProwlarrIndexersApis.indexer(it).updateIndexers(indexerBulk)
            }
            add("DELETE", "api/v1/indexer/1") {
                ProwlarrIndexersApis.indexer(it).deleteIndexer(1)
            }
            add("DELETE", "api/v1/indexer/bulk", expectedBody = indexerBulkBody) {
                ProwlarrIndexersApis.indexer(it).deleteIndexers(indexerBulk)
            }
            add(
                "POST", "api/v1/indexer/test", query = forceTest,
                expectedBody = indexerBody,
            ) {
                ProwlarrIndexersApis.indexer(it).testIndexer(true, indexer)
            }
            add("POST", "api/v1/indexer/testall") {
                ProwlarrIndexersApis.indexer(it).testAllIndexers()
            }
            add(
                "POST", "api/v1/indexer/action/specAction",
                expectedBody = indexerBody,
            ) {
                ProwlarrIndexersApis.indexer(it).indexerAction("specAction", indexer)
            }

            add(
                "GET", "api/v1/indexer/categories",
                fixture = "indexer-default-categories/response.json",
                decode = { text -> ArrJson.decodeFromString<List<IndexerCategory>>(text) },
            ) {
                ProwlarrIndexersApis.indexerDefaultCategories(it).getCategories()
            }

            add(
                "GET", "api/v1/indexerstatus",
                fixture = "indexer-status/response.json",
                decode = { text -> ArrJson.decodeFromString<List<IndexerStatusResource>>(text) },
            ) {
                ProwlarrIndexersApis.indexerStatus(it).getIndexerStatuses()
            }

            val proxyList: (String) -> Any? = {
                ArrJson.decodeFromString<List<IndexerProxyResource>>(it)
            }
            add(
                "GET", "api/v1/indexerproxy",
                fixture = "indexer-proxy/list.json", decode = proxyList,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).getIndexerProxies()
            }
            val oneProxy: (String) -> Any? = {
                ArrJson.decodeFromString<IndexerProxyResource>(it)
            }
            add(
                "GET", "api/v1/indexerproxy/1",
                fixture = "indexer-proxy/response.json", decode = oneProxy,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).getIndexerProxy(1)
            }
            add(
                "GET", "api/v1/indexerproxy/schema",
                fixture = "indexer-proxy/schema.json", decode = proxyList,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).getIndexerProxySchemas()
            }
            add(
                "POST", "api/v1/indexerproxy",
                fixture = "indexer-proxy/response.json", decode = oneProxy,
                expectedBody = proxyBody,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).createIndexerProxy(null, proxy)
            }
            add(
                "PUT", "api/v1/indexerproxy/1",
                fixture = "indexer-proxy/response.json", decode = oneProxy,
                expectedBody = proxyBody,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).updateIndexerProxy("1", null, proxy)
            }
            add("DELETE", "api/v1/indexerproxy/1") {
                ProwlarrIndexersApis.indexerProxy(it).deleteIndexerProxy(1)
            }
            add(
                "POST", "api/v1/indexerproxy/test", query = forceTest,
                expectedBody = proxyBody,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).testIndexerProxy(true, proxy)
            }
            add("POST", "api/v1/indexerproxy/testall") {
                ProwlarrIndexersApis.indexerProxy(it).testAllIndexerProxies()
            }
            add(
                "POST", "api/v1/indexerproxy/action/specAction",
                expectedBody = proxyBody,
            ) {
                ProwlarrIndexersApis.indexerProxy(it).indexerProxyAction("specAction", proxy)
            }

            val profileList: (String) -> Any? = {
                ArrJson.decodeFromString<List<AppProfileResource>>(it)
            }
            val oneProfile: (String) -> Any? = {
                ArrJson.decodeFromString<AppProfileResource>(it)
            }
            add(
                "GET", "api/v1/appprofile",
                fixture = "app-profile/list.json", decode = profileList,
            ) {
                ProwlarrIndexersApis.appProfile(it).getAppProfiles()
            }
            add("GET", "api/v1/appprofile/1", fixture = "app-profile/response.json",
                decode = oneProfile) {
                ProwlarrIndexersApis.appProfile(it).getAppProfile(1)
            }
            add("GET", "api/v1/appprofile/schema", fixture = "app-profile/schema.json",
                decode = oneProfile) {
                ProwlarrIndexersApis.appProfile(it).getAppProfileSchema()
            }
            add(
                "POST", "api/v1/appprofile",
                fixture = "app-profile/response.json", decode = oneProfile,
                expectedBody = profileBody,
            ) {
                ProwlarrIndexersApis.appProfile(it).createAppProfile(profile)
            }
            add(
                "PUT", "api/v1/appprofile/1",
                fixture = "app-profile/response.json", decode = oneProfile,
                expectedBody = profileBody,
            ) {
                ProwlarrIndexersApis.appProfile(it).updateAppProfile("1", profile)
            }
            add("DELETE", "api/v1/appprofile/1") {
                ProwlarrIndexersApis.appProfile(it).deleteAppProfile(1)
            }

            val applicationList: (String) -> Any? = {
                ArrJson.decodeFromString<List<ApplicationResource>>(it)
            }
            val oneApplication: (String) -> Any? = {
                ArrJson.decodeFromString<ApplicationResource>(it)
            }
            add(
                "GET", "api/v1/applications",
                fixture = "application/list.json", decode = applicationList,
            ) {
                ProwlarrIndexersApis.application(it).getApplications()
            }
            add("GET", "api/v1/applications/1", fixture = "application/response.json",
                decode = oneApplication) {
                ProwlarrIndexersApis.application(it).getApplication(1)
            }
            add(
                "GET", "api/v1/applications/schema",
                fixture = "application/schema.json", decode = applicationList,
            ) {
                ProwlarrIndexersApis.application(it).getApplicationSchemas()
            }
            add(
                "POST", "api/v1/applications", query = forceSave,
                fixture = "application/response.json", decode = oneApplication,
                expectedBody = applicationBody,
            ) {
                ProwlarrIndexersApis.application(it).createApplication(true, application)
            }
            add(
                "PUT", "api/v1/applications/1",
                fixture = "application/response.json", decode = oneApplication,
                expectedBody = applicationBody,
            ) {
                ProwlarrIndexersApis.application(it).updateApplication("1", null, application)
            }
            add(
                "PUT", "api/v1/applications/bulk",
                fixture = "application/response.json", decode = oneApplication,
                expectedBody = applicationBulkBody,
            ) {
                ProwlarrIndexersApis.application(it).updateApplications(applicationBulk)
            }
            add("DELETE", "api/v1/applications/1") {
                ProwlarrIndexersApis.application(it).deleteApplication(1)
            }
            add(
                "DELETE", "api/v1/applications/bulk",
                expectedBody = applicationBulkBody,
            ) {
                ProwlarrIndexersApis.application(it).deleteApplications(applicationBulk)
            }
            add(
                "POST", "api/v1/applications/test", query = forceTest,
                expectedBody = applicationBody,
            ) {
                ProwlarrIndexersApis.application(it).testApplication(true, application)
            }
            add("POST", "api/v1/applications/testall") {
                ProwlarrIndexersApis.application(it).testAllApplications()
            }
            add(
                "POST", "api/v1/applications/action/specAction",
                expectedBody = applicationBody,
            ) {
                ProwlarrIndexersApis.application(it).applicationAction("specAction", application)
            }

            val tagList: (String) -> Any? = {
                ArrJson.decodeFromString<List<TagResource>>(it)
            }
            val oneTag: (String) -> Any? = { ArrJson.decodeFromString<TagResource>(it) }
            add("GET", "api/v1/tag", fixture = "tag/list.json", decode = tagList) {
                ProwlarrIndexersApis.tag(it).getTags()
            }
            add(
                "GET", "api/v1/tag/1", fixture = "tag/response.json", decode = oneTag,
            ) {
                ProwlarrIndexersApis.tag(it).getTag(1)
            }
            add(
                "POST", "api/v1/tag",
                fixture = "tag/response.json", decode = oneTag, expectedBody = tagBody,
            ) {
                ProwlarrIndexersApis.tag(it).createTag(tag)
            }
            add(
                "PUT", "api/v1/tag/1",
                fixture = "tag/response.json", decode = oneTag, expectedBody = tagBody,
            ) {
                ProwlarrIndexersApis.tag(it).updateTag("1", tag)
            }
            add("DELETE", "api/v1/tag/1") {
                ProwlarrIndexersApis.tag(it).deleteTag(1)
            }

            val detailList: (String) -> Any? = {
                ArrJson.decodeFromString<List<TagDetailsResource>>(it)
            }
            add(
                "GET", "api/v1/tag/detail",
                fixture = "tag-details/list.json", decode = detailList,
            ) {
                ProwlarrIndexersApis.tagDetails(it).getTagDetails()
            }
            add(
                "GET", "api/v1/tag/detail/1",
                fixture = "tag-details/response.json",
                decode = { text -> ArrJson.decodeFromString<TagDetailsResource>(text) },
            ) {
                ProwlarrIndexersApis.tagDetails(it).getTagDetail(1)
            }
            return cases.map { arrayOf(it) }
        }
    }
}

/**
 * One operation's wire expectations: method, path, query values, response
 * fixture with its decoder, and the request body the operation must send.
 */
data class IndexerCase(
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
