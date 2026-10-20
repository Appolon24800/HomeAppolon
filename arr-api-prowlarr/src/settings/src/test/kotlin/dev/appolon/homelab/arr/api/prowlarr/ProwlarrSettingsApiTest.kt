package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.DevelopmentConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.api.prowlarr.model.UiConfigResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
 * Every operation under the nine settings-group tags runs one happy path
 * and one error path against MockWebServer. Fixtures are synthetic JSON that
 * validates against the bundled OpenAPI schemas; the spec ships no examples.
 * The filesystem and localization dictionary routes declare no response
 * schema, so their fixtures document the shape the server sends and the
 * clients pass it through untouched.
 */
@RunWith(Parameterized::class)
class ProwlarrSettingsApiTest(private val case: SettingsCase) {
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
            val downloadClient = ArrJson.decodeFromString<DownloadClientResource>(
                fixtureText("download-client/response.json")
            )
            val downloadClientBulk = DownloadClientBulkResource(
                ids = listOf(1, 2),
                tags = listOf(3),
                applyTags = ApplyTags.ADD,
                enable = true,
                priority = 40,
            )
            val downloadClientConfig = ArrJson.decodeFromString<DownloadClientConfigResource>(
                fixtureText("download-client-config/response.json")
            )
            val notification = ArrJson.decodeFromString<NotificationResource>(
                fixtureText("notification/response.json")
            )
            val hostConfig = ArrJson.decodeFromString<HostConfigResource>(
                fixtureText("host-config/response.json")
            )
            val developmentConfig = ArrJson.decodeFromString<DevelopmentConfigResource>(
                fixtureText("development-config/response.json")
            )
            val uiConfig = ArrJson.decodeFromString<UiConfigResource>(
                fixtureText("ui-config/response.json")
            )
            val downloadClientBody = ArrJson.encodeToString(downloadClient)
            val downloadClientBulkBody = ArrJson.encodeToString(downloadClientBulk)
            val downloadClientConfigBody = ArrJson.encodeToString(downloadClientConfig)
            val notificationBody = ArrJson.encodeToString(notification)
            val hostConfigBody = ArrJson.encodeToString(hostConfig)
            val developmentConfigBody = ArrJson.encodeToString(developmentConfig)
            val uiConfigBody = ArrJson.encodeToString(uiConfig)
            val forceSave = mapOf("forceSave" to listOf("true"))
            val forceTest = mapOf("forceTest" to listOf("true"))
            val text = "text/plain".toMediaType()
            val cases = mutableListOf<SettingsCase>()

            fun add(
                method: String,
                path: String,
                query: Map<String, List<String>> = emptyMap(),
                fixture: String? = null,
                decode: ((String) -> Any?)? = null,
                expectedBody: String? = null,
                call: suspend (Retrofit) -> Any?,
            ) {
                cases += SettingsCase(
                    method, path, query, fixture, decode, expectedBody, call
                )
            }

            val clientList: (String) -> Any? = {
                ArrJson.decodeFromString<List<DownloadClientResource>>(it)
            }
            val oneClient: (String) -> Any? = {
                ArrJson.decodeFromString<DownloadClientResource>(it)
            }
            add(
                "GET", "api/v1/downloadclient",
                fixture = "download-client/list.json", decode = clientList,
            ) {
                ProwlarrSettingsApis.downloadClient(it).getDownloadClients()
            }
            add(
                "GET", "api/v1/downloadclient/1",
                fixture = "download-client/response.json", decode = oneClient,
            ) {
                ProwlarrSettingsApis.downloadClient(it).getDownloadClient(1)
            }
            add(
                "GET", "api/v1/downloadclient/schema",
                fixture = "download-client/schema.json", decode = clientList,
            ) {
                ProwlarrSettingsApis.downloadClient(it).getDownloadClientSchemas()
            }
            add(
                "POST", "api/v1/downloadclient", query = forceSave,
                fixture = "download-client/response.json", decode = oneClient,
                expectedBody = downloadClientBody,
            ) {
                ProwlarrSettingsApis.downloadClient(it).createDownloadClient(true, downloadClient)
            }
            add(
                "PUT", "api/v1/downloadclient/1",
                fixture = "download-client/response.json", decode = oneClient,
                expectedBody = downloadClientBody,
            ) {
                ProwlarrSettingsApis.downloadClient(it)
                    .updateDownloadClient("1", null, downloadClient)
            }
            add(
                "PUT", "api/v1/downloadclient/bulk",
                fixture = "download-client/response.json", decode = oneClient,
                expectedBody = downloadClientBulkBody,
            ) {
                ProwlarrSettingsApis.downloadClient(it).updateDownloadClients(downloadClientBulk)
            }
            add("DELETE", "api/v1/downloadclient/1") {
                ProwlarrSettingsApis.downloadClient(it).deleteDownloadClient(1)
            }
            add(
                "DELETE", "api/v1/downloadclient/bulk",
                expectedBody = downloadClientBulkBody,
            ) {
                ProwlarrSettingsApis.downloadClient(it).deleteDownloadClients(downloadClientBulk)
            }
            add(
                "POST", "api/v1/downloadclient/test", query = forceTest,
                expectedBody = downloadClientBody,
            ) {
                ProwlarrSettingsApis.downloadClient(it)
                    .testDownloadClient(true, downloadClient)
            }
            add("POST", "api/v1/downloadclient/testall") {
                ProwlarrSettingsApis.downloadClient(it).testAllDownloadClients()
            }
            add(
                "POST", "api/v1/downloadclient/action/specAction",
                expectedBody = downloadClientBody,
            ) {
                ProwlarrSettingsApis.downloadClient(it)
                    .downloadClientAction("specAction", downloadClient)
            }

            val oneDownloadClientConfig: (String) -> Any? = {
                ArrJson.decodeFromString<DownloadClientConfigResource>(it)
            }
            add(
                "GET", "api/v1/config/downloadclient",
                fixture = "download-client-config/response.json",
                decode = oneDownloadClientConfig,
            ) {
                ProwlarrSettingsApis.downloadClientConfig(it).getDownloadClientConfig()
            }
            add(
                "GET", "api/v1/config/downloadclient/1",
                fixture = "download-client-config/response.json",
                decode = oneDownloadClientConfig,
            ) {
                ProwlarrSettingsApis.downloadClientConfig(it).getDownloadClientConfig(1)
            }
            add(
                "PUT", "api/v1/config/downloadclient/1",
                fixture = "download-client-config/response.json",
                decode = oneDownloadClientConfig,
                expectedBody = downloadClientConfigBody,
            ) {
                ProwlarrSettingsApis.downloadClientConfig(it)
                    .updateDownloadClientConfig("1", downloadClientConfig)
            }

            val notificationList: (String) -> Any? = {
                ArrJson.decodeFromString<List<NotificationResource>>(it)
            }
            val oneNotification: (String) -> Any? = {
                ArrJson.decodeFromString<NotificationResource>(it)
            }
            add(
                "GET", "api/v1/notification",
                fixture = "notification/list.json", decode = notificationList,
            ) {
                ProwlarrSettingsApis.notification(it).getNotifications()
            }
            add(
                "GET", "api/v1/notification/1",
                fixture = "notification/response.json", decode = oneNotification,
            ) {
                ProwlarrSettingsApis.notification(it).getNotification(1)
            }
            add(
                "GET", "api/v1/notification/schema",
                fixture = "notification/schema.json", decode = notificationList,
            ) {
                ProwlarrSettingsApis.notification(it).getNotificationSchemas()
            }
            add(
                "POST", "api/v1/notification", query = forceSave,
                fixture = "notification/response.json", decode = oneNotification,
                expectedBody = notificationBody,
            ) {
                ProwlarrSettingsApis.notification(it).createNotification(true, notification)
            }
            add(
                "PUT", "api/v1/notification/1",
                fixture = "notification/response.json", decode = oneNotification,
                expectedBody = notificationBody,
            ) {
                ProwlarrSettingsApis.notification(it)
                    .updateNotification("1", null, notification)
            }
            add("DELETE", "api/v1/notification/1") {
                ProwlarrSettingsApis.notification(it).deleteNotification(1)
            }
            add(
                "POST", "api/v1/notification/test", query = forceTest,
                expectedBody = notificationBody,
            ) {
                ProwlarrSettingsApis.notification(it).testNotification(true, notification)
            }
            add("POST", "api/v1/notification/testall") {
                ProwlarrSettingsApis.notification(it).testAllNotifications()
            }
            add(
                "POST", "api/v1/notification/action/specAction",
                expectedBody = notificationBody,
            ) {
                ProwlarrSettingsApis.notification(it)
                    .notificationAction("specAction", notification)
            }

            val oneHostConfig: (String) -> Any? = {
                ArrJson.decodeFromString<HostConfigResource>(it)
            }
            add(
                "GET", "api/v1/config/host",
                fixture = "host-config/response.json", decode = oneHostConfig,
            ) {
                ProwlarrSettingsApis.hostConfig(it).getHostConfig()
            }
            add(
                "GET", "api/v1/config/host/1",
                fixture = "host-config/response.json", decode = oneHostConfig,
            ) {
                ProwlarrSettingsApis.hostConfig(it).getHostConfig(1)
            }
            add(
                "PUT", "api/v1/config/host/1",
                fixture = "host-config/response.json", decode = oneHostConfig,
                expectedBody = hostConfigBody,
            ) {
                ProwlarrSettingsApis.hostConfig(it).updateHostConfig("1", hostConfig)
            }

            val oneDevelopmentConfig: (String) -> Any? = {
                ArrJson.decodeFromString<DevelopmentConfigResource>(it)
            }
            add(
                "GET", "api/v1/config/development",
                fixture = "development-config/response.json", decode = oneDevelopmentConfig,
            ) {
                ProwlarrSettingsApis.developmentConfig(it).getDevelopmentConfig()
            }
            add(
                "GET", "api/v1/config/development/1",
                fixture = "development-config/response.json", decode = oneDevelopmentConfig,
            ) {
                ProwlarrSettingsApis.developmentConfig(it).getDevelopmentConfig(1)
            }
            add(
                "PUT", "api/v1/config/development/1",
                fixture = "development-config/response.json", decode = oneDevelopmentConfig,
                expectedBody = developmentConfigBody,
            ) {
                ProwlarrSettingsApis.developmentConfig(it)
                    .updateDevelopmentConfig("1", developmentConfig)
            }

            val oneUiConfig: (String) -> Any? = {
                ArrJson.decodeFromString<UiConfigResource>(it)
            }
            add(
                "GET", "api/v1/config/ui",
                fixture = "ui-config/response.json", decode = oneUiConfig,
            ) {
                ProwlarrSettingsApis.uiConfig(it).getUiConfig()
            }
            add(
                "GET", "api/v1/config/ui/1",
                fixture = "ui-config/response.json", decode = oneUiConfig,
            ) {
                ProwlarrSettingsApis.uiConfig(it).getUiConfig(1)
            }
            add(
                "PUT", "api/v1/config/ui/1",
                fixture = "ui-config/response.json", decode = oneUiConfig,
                expectedBody = uiConfigBody,
            ) {
                ProwlarrSettingsApis.uiConfig(it).updateUiConfig("1", uiConfig)
            }

            add(
                "POST", "login", query = mapOf("returnUrl" to listOf("/")),
            ) {
                ProwlarrSettingsApis.authentication(it).login(
                    "spec-user".toRequestBody(text),
                    "spec-password".toRequestBody(text),
                    "true".toRequestBody(text),
                    "/",
                )
            }
            add("GET", "logout") {
                ProwlarrSettingsApis.authentication(it).logout()
            }

            add(
                "GET", "api/v1/filesystem",
                query = mapOf(
                    "path" to listOf("/media"),
                    "includeFiles" to listOf("true"),
                    "allowFoldersWithoutTrailingSlashes" to listOf("false"),
                ),
                fixture = "filesystem/listing.json",
                decode = { text -> ArrJson.decodeFromString<JsonObject>(text) },
            ) {
                ProwlarrSettingsApis.fileSystem(it)
                    .getFileSystem("/media", true, false)
            }
            add(
                "GET", "api/v1/filesystem/type",
                query = mapOf("path" to listOf("/media")),
                fixture = "filesystem/type.json",
                decode = { text -> ArrJson.decodeFromString<JsonElement>(text) },
            ) {
                ProwlarrSettingsApis.fileSystem(it).getFileSystemType("/media")
            }

            add(
                "GET", "api/v1/localization",
                fixture = "localization/dictionary.json",
                decode = { text -> ArrJson.decodeFromString<JsonObject>(text) },
            ) {
                ProwlarrSettingsApis.localization(it).getLocalization()
            }
            add(
                "GET", "api/v1/localization/options",
                fixture = "localization/options.json",
                decode = { text -> ArrJson.decodeFromString<List<LocalizationOption>>(text) },
            ) {
                ProwlarrSettingsApis.localization(it).getLocalizationOptions()
            }
            return cases.map { arrayOf(it) }
        }
    }
}

/**
 * One operation's wire expectations: method, path, query values, response
 * fixture with its decoder, and the request body the operation must send.
 */
data class SettingsCase(
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
