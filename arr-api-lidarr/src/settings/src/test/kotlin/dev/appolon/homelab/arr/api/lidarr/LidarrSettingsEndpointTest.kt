package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.lidarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataResource
import dev.appolon.homelab.arr.api.lidarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.NotificationResource
import dev.appolon.homelab.arr.api.lidarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.api.lidarr.model.UiConfigResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun settingsFixture(path: String): String = checkNotNull(
    LidarrSettingsEndpointTest::class.java.getResourceAsStream("/fixtures/lidarr/$path.json")
).bufferedReader().use { it.readText() }

internal inline fun <reified T> settingsDto(path: String): T =
    ArrJson.decodeFromString(settingsFixture(path))

internal fun settingsRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/lidarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Each row is one spec operation under the settings tag groups. */
data class SettingsEndpointCase(
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
class LidarrSettingsEndpointTest(private val case: SettingsEndpointCase) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun happyPath() = runTest {
        server.enqueue(MockResponse().setBody(case.response))
        case.call(settingsRetrofit(server))
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
            val autoTaggings = settingsDto<List<AutoTaggingResource>>("autotagging/getAutoTaggings")
            val hostConfig = settingsDto<HostConfigResource>("hostconfig/getHostConfig")
            val mediaManagement = settingsDto<MediaManagementConfigResource>(
                "mediamanagementconfig/getMediaManagementConfig"
            )
            val naming = settingsDto<NamingConfigResource>("namingconfig/getNamingConfig")
            val namingExamples =
                "{\"standardTrackFormat\":\"Artist - Album - 01 - Track\",\"artistFolderFormat\":\"Artist\"}"
            val ui = settingsDto<UiConfigResource>("uiconfig/getUiConfig")
            val metadatas = settingsDto<List<MetadataResource>>("metadata/getMetadatas")
            val notifications = settingsDto<List<NotificationResource>>(
                "notification/getNotifications"
            )
            val clients =
                settingsDto<List<DownloadClientResource>>("downloadclient/getDownloadClients")
            val clientConfig = settingsDto<DownloadClientConfigResource>(
                "downloadclientconfig/getDownloadClientConfig"
            )
            val indexers = settingsDto<List<IndexerResource>>("indexer/getIndexers")
            val indexerConfig = settingsDto<IndexerConfigResource>(
                "indexerconfig/getIndexerConfig"
            )
            val indexerFlags = settingsDto<List<IndexerFlagResource>>("indexerflag/getIndexerFlags")
            val mappings = settingsDto<List<RemotePathMappingResource>>(
                "remotepathmapping/getRemotePathMappings"
            )
            val rootFolders = settingsDto<List<RootFolderResource>>("rootfolder/getRootFolders")
            val tags = settingsDto<List<TagResource>>("tag/getTags")
            val tagDetails = settingsDto<List<TagDetailsResource>>("tagdetails/getTagDetails")
            val clientBulk = DownloadClientBulkResource(
                ids = listOf(1, 2), tags = listOf(2), applyTags = ApplyTags.ADD, priority = 10,
            )
            val indexerBulk = IndexerBulkResource(
                ids = listOf(1, 2), tags = listOf(2), applyTags = ApplyTags.REPLACE,
                enableRss = false,
            )
            fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
                pairs.groupBy({ it.first }, { it.second })
            return listOf(
                SettingsEndpointCase(
                    "getAutoTaggings", "GET", "api/v1/autotagging",
                    settingsFixture("autotagging/getAutoTaggings"),
                ) { r ->
                    assertEquals(
                        autoTaggings, LidarrSystemApis.autoTagging(r).getAutoTaggings()
                    )
                },
                SettingsEndpointCase(
                    "getAutoTagging", "GET", "api/v1/autotagging/1",
                    ArrJson.encodeToString(autoTaggings.first()),
                ) { r ->
                    assertEquals(
                        autoTaggings.first(), LidarrSystemApis.autoTagging(r).getAutoTagging(1)
                    )
                },
                SettingsEndpointCase(
                    "addAutoTagging", "POST", "api/v1/autotagging",
                    ArrJson.encodeToString(autoTaggings.first()),
                    body = ArrJson.encodeToJsonElement(autoTaggings.first()),
                ) { r ->
                    assertEquals(
                        autoTaggings.first(),
                        LidarrSystemApis.autoTagging(r).addAutoTagging(autoTaggings.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateAutoTagging", "PUT", "api/v1/autotagging/1",
                    ArrJson.encodeToString(autoTaggings.first()),
                    body = ArrJson.encodeToJsonElement(autoTaggings.first()),
                ) { r ->
                    assertEquals(
                        autoTaggings.first(),
                        LidarrSystemApis.autoTagging(r)
                            .updateAutoTagging("1", autoTaggings.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteAutoTagging", "DELETE", "api/v1/autotagging/1",
                ) { r -> LidarrSystemApis.autoTagging(r).deleteAutoTagging(1) },
                SettingsEndpointCase(
                    "getAutoTaggingSchema", "GET", "api/v1/autotagging/schema",
                    "{\"specifications\":[]}",
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement("{\"specifications\":[]}"),
                        LidarrSystemApis.autoTagging(r).getAutoTaggingSchema(),
                    )
                },
                SettingsEndpointCase(
                    "getHostConfig", "GET", "api/v1/config/host",
                    settingsFixture("hostconfig/getHostConfig"),
                ) { r ->
                    assertEquals(hostConfig, LidarrSystemApis.hostConfig(r).getHostConfig())
                },
                SettingsEndpointCase(
                    "getHostConfigById", "GET", "api/v1/config/host/1",
                    settingsFixture("hostconfig/getHostConfig"),
                ) { r ->
                    assertEquals(
                        hostConfig, LidarrSystemApis.hostConfig(r).getHostConfigById(1)
                    )
                },
                SettingsEndpointCase(
                    "updateHostConfig", "PUT", "api/v1/config/host/1",
                    settingsFixture("hostconfig/getHostConfig"),
                    body = ArrJson.encodeToJsonElement(hostConfig),
                ) { r ->
                    assertEquals(
                        hostConfig, LidarrSystemApis.hostConfig(r)
                            .updateHostConfig("1", hostConfig)
                    )
                },
                SettingsEndpointCase(
                    "getMediaManagementConfig", "GET", "api/v1/config/mediamanagement",
                    settingsFixture("mediamanagementconfig/getMediaManagementConfig"),
                ) { r ->
                    assertEquals(
                        mediaManagement,
                        LidarrSystemApis.mediaManagementConfig(r).getMediaManagementConfig(),
                    )
                },
                SettingsEndpointCase(
                    "getMediaManagementConfigById", "GET", "api/v1/config/mediamanagement/1",
                    settingsFixture("mediamanagementconfig/getMediaManagementConfig"),
                ) { r ->
                    assertEquals(
                        mediaManagement,
                        LidarrSystemApis.mediaManagementConfig(r)
                            .getMediaManagementConfigById(1),
                    )
                },
                SettingsEndpointCase(
                    "updateMediaManagementConfig", "PUT", "api/v1/config/mediamanagement/1",
                    settingsFixture("mediamanagementconfig/getMediaManagementConfig"),
                    body = ArrJson.encodeToJsonElement(mediaManagement),
                ) { r ->
                    assertEquals(
                        mediaManagement,
                        LidarrSystemApis.mediaManagementConfig(r)
                            .updateMediaManagementConfig("1", mediaManagement),
                    )
                },
                SettingsEndpointCase(
                    "getNamingConfig", "GET", "api/v1/config/naming",
                    settingsFixture("namingconfig/getNamingConfig"),
                ) { r ->
                    assertEquals(naming, LidarrSystemApis.namingConfig(r).getNamingConfig())
                },
                SettingsEndpointCase(
                    "getNamingConfigById", "GET", "api/v1/config/naming/1",
                    settingsFixture("namingconfig/getNamingConfig"),
                ) { r ->
                    assertEquals(
                        naming, LidarrSystemApis.namingConfig(r).getNamingConfigById(1)
                    )
                },
                SettingsEndpointCase(
                    "updateNamingConfig", "PUT", "api/v1/config/naming/1",
                    settingsFixture("namingconfig/getNamingConfig"),
                    body = ArrJson.encodeToJsonElement(naming),
                ) { r ->
                    assertEquals(
                        naming, LidarrSystemApis.namingConfig(r)
                            .updateNamingConfig("1", naming)
                    )
                },
                SettingsEndpointCase(
                    "getNamingExamples", "GET", "api/v1/config/naming/examples",
                    namingExamples,
                    query(
                        "renameTracks" to "true",
                        "standardTrackFormat" to "{Artist Name} - {Album Title}",
                        "numberStyle" to "00",
                    ),
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(namingExamples),
                        LidarrSystemApis.namingConfig(r).getNamingExamples(
                            renameTracks = true,
                            standardTrackFormat = "{Artist Name} - {Album Title}",
                            numberStyle = "00",
                        ),
                    )
                },
                SettingsEndpointCase(
                    "getUiConfig", "GET", "api/v1/config/ui",
                    settingsFixture("uiconfig/getUiConfig"),
                ) { r -> assertEquals(ui, LidarrSystemApis.uiConfig(r).getUiConfig()) },
                SettingsEndpointCase(
                    "getUiConfigById", "GET", "api/v1/config/ui/1",
                    settingsFixture("uiconfig/getUiConfig"),
                ) { r ->
                    assertEquals(ui, LidarrSystemApis.uiConfig(r).getUiConfigById(1))
                },
                SettingsEndpointCase(
                    "updateUiConfig", "PUT", "api/v1/config/ui/1",
                    settingsFixture("uiconfig/getUiConfig"),
                    body = ArrJson.encodeToJsonElement(ui),
                ) { r ->
                    assertEquals(
                        ui, LidarrSystemApis.uiConfig(r).updateUiConfig("1", ui)
                    )
                },
                SettingsEndpointCase(
                    "getMetadatas", "GET", "api/v1/metadata",
                    settingsFixture("metadata/getMetadatas"),
                ) { r -> assertEquals(metadatas, LidarrSystemApis.metadata(r).getMetadatas()) },
                SettingsEndpointCase(
                    "getMetadata", "GET", "api/v1/metadata/1",
                    ArrJson.encodeToString(metadatas.first()),
                ) { r ->
                    assertEquals(
                        metadatas.first(), LidarrSystemApis.metadata(r).getMetadata(1)
                    )
                },
                SettingsEndpointCase(
                    "addMetadata", "POST", "api/v1/metadata",
                    ArrJson.encodeToString(metadatas.first()),
                    query("forceSave" to "true"),
                    ArrJson.encodeToJsonElement(metadatas.first()),
                ) { r ->
                    assertEquals(
                        metadatas.first(),
                        LidarrSystemApis.metadata(r).addMetadata(true, metadatas.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateMetadata", "PUT", "api/v1/metadata/1",
                    ArrJson.encodeToString(metadatas.first()),
                    query("forceSave" to "false"),
                    ArrJson.encodeToJsonElement(metadatas.first()),
                ) { r ->
                    assertEquals(
                        metadatas.first(),
                        LidarrSystemApis.metadata(r).updateMetadata(1, false, metadatas.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteMetadata", "DELETE", "api/v1/metadata/1",
                ) { r -> LidarrSystemApis.metadata(r).deleteMetadata(1) },
                SettingsEndpointCase(
                    "getMetadataSchema", "GET", "api/v1/metadata/schema",
                    settingsFixture("metadata/getMetadatas"),
                ) { r ->
                    assertEquals(
                        metadatas, LidarrSystemApis.metadata(r).getMetadataSchema()
                    )
                },
                SettingsEndpointCase(
                    "testMetadata", "POST", "api/v1/metadata/test",
                    queries = query("forceTest" to "true"),
                    body = ArrJson.encodeToJsonElement(metadatas.first()),
                ) { r ->
                    LidarrSystemApis.metadata(r).testMetadata(true, metadatas.first())
                },
                SettingsEndpointCase(
                    "testAllMetadata", "POST", "api/v1/metadata/testall",
                ) { r -> LidarrSystemApis.metadata(r).testAllMetadata() },
                SettingsEndpointCase(
                    "metadataAction", "POST", "api/v1/metadata/action/search",
                    body = ArrJson.encodeToJsonElement(metadatas.first()),
                ) { r ->
                    LidarrSystemApis.metadata(r).metadataAction("search", metadatas.first())
                },
                SettingsEndpointCase(
                    "getNotifications", "GET", "api/v1/notification",
                    settingsFixture("notification/getNotifications"),
                ) { r ->
                    assertEquals(
                        notifications, LidarrSystemApis.notification(r).getNotifications()
                    )
                },
                SettingsEndpointCase(
                    "getNotification", "GET", "api/v1/notification/1",
                    ArrJson.encodeToString(notifications.first()),
                ) { r ->
                    assertEquals(
                        notifications.first(),
                        LidarrSystemApis.notification(r).getNotification(1),
                    )
                },
                SettingsEndpointCase(
                    "addNotification", "POST", "api/v1/notification",
                    ArrJson.encodeToString(notifications.first()),
                    query("forceSave" to "true"),
                    ArrJson.encodeToJsonElement(notifications.first()),
                ) { r ->
                    assertEquals(
                        notifications.first(),
                        LidarrSystemApis.notification(r)
                            .addNotification(true, notifications.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateNotification", "PUT", "api/v1/notification/1",
                    ArrJson.encodeToString(notifications.first()),
                    query("forceSave" to "false"),
                    ArrJson.encodeToJsonElement(notifications.first()),
                ) { r ->
                    assertEquals(
                        notifications.first(),
                        LidarrSystemApis.notification(r)
                            .updateNotification(1, false, notifications.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteNotification", "DELETE", "api/v1/notification/1",
                ) { r -> LidarrSystemApis.notification(r).deleteNotification(1) },
                SettingsEndpointCase(
                    "getNotificationSchema", "GET", "api/v1/notification/schema",
                    settingsFixture("notification/getNotifications"),
                ) { r ->
                    assertEquals(
                        notifications, LidarrSystemApis.notification(r).getNotificationSchema()
                    )
                },
                SettingsEndpointCase(
                    "testNotification", "POST", "api/v1/notification/test",
                    queries = query("forceTest" to "true"),
                    body = ArrJson.encodeToJsonElement(notifications.first()),
                ) { r ->
                    LidarrSystemApis.notification(r).testNotification(true, notifications.first())
                },
                SettingsEndpointCase(
                    "testAllNotifications", "POST", "api/v1/notification/testall",
                ) { r -> LidarrSystemApis.notification(r).testAllNotifications() },
                SettingsEndpointCase(
                    "notificationAction", "POST", "api/v1/notification/action/test",
                    body = ArrJson.encodeToJsonElement(notifications.first()),
                ) { r ->
                    LidarrSystemApis.notification(r)
                        .notificationAction("test", notifications.first())
                },
                SettingsEndpointCase(
                    "getDownloadClients", "GET", "api/v1/downloadclient",
                    settingsFixture("downloadclient/getDownloadClients"),
                ) { r ->
                    assertEquals(clients, LidarrSystemApis.downloadClient(r).getDownloadClients())
                },
                SettingsEndpointCase(
                    "getDownloadClient", "GET", "api/v1/downloadclient/1",
                    ArrJson.encodeToString(clients.first()),
                ) { r ->
                    assertEquals(
                        clients.first(), LidarrSystemApis.downloadClient(r).getDownloadClient(1)
                    )
                },
                SettingsEndpointCase(
                    "addDownloadClient", "POST", "api/v1/downloadclient",
                    ArrJson.encodeToString(clients.first()),
                    query("forceSave" to "true"),
                    ArrJson.encodeToJsonElement(clients.first()),
                ) { r ->
                    assertEquals(
                        clients.first(),
                        LidarrSystemApis.downloadClient(r).addDownloadClient(true, clients.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateDownloadClient", "PUT", "api/v1/downloadclient/1",
                    ArrJson.encodeToString(clients.first()),
                    query("forceSave" to "false"),
                    ArrJson.encodeToJsonElement(clients.first()),
                ) { r ->
                    assertEquals(
                        clients.first(),
                        LidarrSystemApis.downloadClient(r)
                            .updateDownloadClient(1, false, clients.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteDownloadClient", "DELETE", "api/v1/downloadclient/1",
                ) { r -> LidarrSystemApis.downloadClient(r).deleteDownloadClient(1) },
                SettingsEndpointCase(
                    "updateDownloadClients", "PUT", "api/v1/downloadclient/bulk",
                    ArrJson.encodeToString(clients.first()),
                    body = ArrJson.encodeToJsonElement(clientBulk),
                ) { r ->
                    assertEquals(
                        clients.first(),
                        LidarrSystemApis.downloadClient(r).updateDownloadClients(clientBulk),
                    )
                },
                SettingsEndpointCase(
                    "deleteDownloadClients", "DELETE", "api/v1/downloadclient/bulk",
                    body = ArrJson.encodeToJsonElement(clientBulk),
                ) { r ->
                    LidarrSystemApis.downloadClient(r).deleteDownloadClients(clientBulk)
                },
                SettingsEndpointCase(
                    "getDownloadClientSchema", "GET", "api/v1/downloadclient/schema",
                    settingsFixture("downloadclient/getDownloadClients"),
                ) { r ->
                    assertEquals(
                        clients, LidarrSystemApis.downloadClient(r).getDownloadClientSchema()
                    )
                },
                SettingsEndpointCase(
                    "testDownloadClient", "POST", "api/v1/downloadclient/test",
                    queries = query("forceTest" to "true"),
                    body = ArrJson.encodeToJsonElement(clients.first()),
                ) { r ->
                    LidarrSystemApis.downloadClient(r).testDownloadClient(true, clients.first())
                },
                SettingsEndpointCase(
                    "testAllDownloadClients", "POST", "api/v1/downloadclient/testall",
                ) { r -> LidarrSystemApis.downloadClient(r).testAllDownloadClients() },
                SettingsEndpointCase(
                    "downloadClientAction", "POST", "api/v1/downloadclient/action/search",
                    body = ArrJson.encodeToJsonElement(clients.first()),
                ) { r ->
                    LidarrSystemApis.downloadClient(r)
                        .downloadClientAction("search", clients.first())
                },
                SettingsEndpointCase(
                    "getDownloadClientConfig", "GET", "api/v1/config/downloadclient",
                    settingsFixture("downloadclientconfig/getDownloadClientConfig"),
                ) { r ->
                    assertEquals(
                        clientConfig,
                        LidarrSystemApis.downloadClientConfig(r).getDownloadClientConfig(),
                    )
                },
                SettingsEndpointCase(
                    "getDownloadClientConfigById", "GET", "api/v1/config/downloadclient/1",
                    settingsFixture("downloadclientconfig/getDownloadClientConfig"),
                ) { r ->
                    assertEquals(
                        clientConfig,
                        LidarrSystemApis.downloadClientConfig(r)
                            .getDownloadClientConfigById(1),
                    )
                },
                SettingsEndpointCase(
                    "updateDownloadClientConfig", "PUT", "api/v1/config/downloadclient/1",
                    settingsFixture("downloadclientconfig/getDownloadClientConfig"),
                    body = ArrJson.encodeToJsonElement(clientConfig),
                ) { r ->
                    assertEquals(
                        clientConfig,
                        LidarrSystemApis.downloadClientConfig(r)
                            .updateDownloadClientConfig("1", clientConfig),
                    )
                },
                SettingsEndpointCase(
                    "getIndexers", "GET", "api/v1/indexer",
                    settingsFixture("indexer/getIndexers"),
                ) { r -> assertEquals(indexers, LidarrSystemApis.indexer(r).getIndexers()) },
                SettingsEndpointCase(
                    "getIndexer", "GET", "api/v1/indexer/1",
                    ArrJson.encodeToString(indexers.first()),
                ) { r ->
                    assertEquals(
                        indexers.first(), LidarrSystemApis.indexer(r).getIndexer(1)
                    )
                },
                SettingsEndpointCase(
                    "addIndexer", "POST", "api/v1/indexer",
                    ArrJson.encodeToString(indexers.first()),
                    query("forceSave" to "true"),
                    ArrJson.encodeToJsonElement(indexers.first()),
                ) { r ->
                    assertEquals(
                        indexers.first(),
                        LidarrSystemApis.indexer(r).addIndexer(true, indexers.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateIndexer", "PUT", "api/v1/indexer/1",
                    ArrJson.encodeToString(indexers.first()),
                    query("forceSave" to "false"),
                    ArrJson.encodeToJsonElement(indexers.first()),
                ) { r ->
                    assertEquals(
                        indexers.first(),
                        LidarrSystemApis.indexer(r).updateIndexer(1, false, indexers.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteIndexer", "DELETE", "api/v1/indexer/1",
                ) { r -> LidarrSystemApis.indexer(r).deleteIndexer(1) },
                SettingsEndpointCase(
                    "updateIndexers", "PUT", "api/v1/indexer/bulk",
                    ArrJson.encodeToString(indexers.first()),
                    body = ArrJson.encodeToJsonElement(indexerBulk),
                ) { r ->
                    assertEquals(
                        indexers.first(), LidarrSystemApis.indexer(r).updateIndexers(indexerBulk)
                    )
                },
                SettingsEndpointCase(
                    "deleteIndexers", "DELETE", "api/v1/indexer/bulk",
                    body = ArrJson.encodeToJsonElement(indexerBulk),
                ) { r -> LidarrSystemApis.indexer(r).deleteIndexers(indexerBulk) },
                SettingsEndpointCase(
                    "getIndexerSchema", "GET", "api/v1/indexer/schema",
                    settingsFixture("indexer/getIndexers"),
                ) { r ->
                    assertEquals(
                        indexers, LidarrSystemApis.indexer(r).getIndexerSchema()
                    )
                },
                SettingsEndpointCase(
                    "testIndexer", "POST", "api/v1/indexer/test",
                    queries = query("forceTest" to "true"),
                    body = ArrJson.encodeToJsonElement(indexers.first()),
                ) { r ->
                    LidarrSystemApis.indexer(r).testIndexer(true, indexers.first())
                },
                SettingsEndpointCase(
                    "testAllIndexers", "POST", "api/v1/indexer/testall",
                ) { r -> LidarrSystemApis.indexer(r).testAllIndexers() },
                SettingsEndpointCase(
                    "indexerAction", "POST", "api/v1/indexer/action/search",
                    body = ArrJson.encodeToJsonElement(indexers.first()),
                ) { r ->
                    LidarrSystemApis.indexer(r).indexerAction("search", indexers.first())
                },
                SettingsEndpointCase(
                    "getIndexerConfig", "GET", "api/v1/config/indexer",
                    settingsFixture("indexerconfig/getIndexerConfig"),
                ) { r ->
                    assertEquals(
                        indexerConfig, LidarrSystemApis.indexerConfig(r).getIndexerConfig()
                    )
                },
                SettingsEndpointCase(
                    "getIndexerConfigById", "GET", "api/v1/config/indexer/1",
                    settingsFixture("indexerconfig/getIndexerConfig"),
                ) { r ->
                    assertEquals(
                        indexerConfig, LidarrSystemApis.indexerConfig(r).getIndexerConfigById(1)
                    )
                },
                SettingsEndpointCase(
                    "updateIndexerConfig", "PUT", "api/v1/config/indexer/1",
                    settingsFixture("indexerconfig/getIndexerConfig"),
                    body = ArrJson.encodeToJsonElement(indexerConfig),
                ) { r ->
                    assertEquals(
                        indexerConfig,
                        LidarrSystemApis.indexerConfig(r)
                            .updateIndexerConfig("1", indexerConfig),
                    )
                },
                SettingsEndpointCase(
                    "getIndexerFlags", "GET", "api/v1/indexerflag",
                    settingsFixture("indexerflag/getIndexerFlags"),
                ) { r ->
                    assertEquals(
                        indexerFlags, LidarrSystemApis.indexerFlag(r).getIndexerFlags()
                    )
                },
                SettingsEndpointCase(
                    "getRemotePathMappings", "GET", "api/v1/remotepathmapping",
                    settingsFixture("remotepathmapping/getRemotePathMappings"),
                ) { r ->
                    assertEquals(
                        mappings, LidarrSystemApis.remotePathMapping(r).getRemotePathMappings()
                    )
                },
                SettingsEndpointCase(
                    "getRemotePathMapping", "GET", "api/v1/remotepathmapping/1",
                    ArrJson.encodeToString(mappings.first()),
                ) { r ->
                    assertEquals(
                        mappings.first(),
                        LidarrSystemApis.remotePathMapping(r).getRemotePathMapping(1),
                    )
                },
                SettingsEndpointCase(
                    "addRemotePathMapping", "POST", "api/v1/remotepathmapping",
                    ArrJson.encodeToString(mappings.first()),
                    body = ArrJson.encodeToJsonElement(mappings.first()),
                ) { r ->
                    assertEquals(
                        mappings.first(),
                        LidarrSystemApis.remotePathMapping(r)
                            .addRemotePathMapping(mappings.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateRemotePathMapping", "PUT", "api/v1/remotepathmapping/1",
                    ArrJson.encodeToString(mappings.first()),
                    body = ArrJson.encodeToJsonElement(mappings.first()),
                ) { r ->
                    assertEquals(
                        mappings.first(),
                        LidarrSystemApis.remotePathMapping(r)
                            .updateRemotePathMapping("1", mappings.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteRemotePathMapping", "DELETE", "api/v1/remotepathmapping/1",
                ) { r -> LidarrSystemApis.remotePathMapping(r).deleteRemotePathMapping(1) },
                SettingsEndpointCase(
                    "getRootFolders", "GET", "api/v1/rootfolder",
                    settingsFixture("rootfolder/getRootFolders"),
                ) { r ->
                    assertEquals(
                        rootFolders, LidarrSystemApis.rootFolder(r).getRootFolders()
                    )
                },
                SettingsEndpointCase(
                    "getRootFolder", "GET", "api/v1/rootfolder/1",
                    ArrJson.encodeToString(rootFolders.first()),
                ) { r ->
                    assertEquals(
                        rootFolders.first(), LidarrSystemApis.rootFolder(r).getRootFolder(1)
                    )
                },
                SettingsEndpointCase(
                    "addRootFolder", "POST", "api/v1/rootfolder",
                    ArrJson.encodeToString(rootFolders.first()),
                    body = ArrJson.encodeToJsonElement(rootFolders.first()),
                ) { r ->
                    assertEquals(
                        rootFolders.first(),
                        LidarrSystemApis.rootFolder(r).addRootFolder(rootFolders.first()),
                    )
                },
                SettingsEndpointCase(
                    "updateRootFolder", "PUT", "api/v1/rootfolder/1",
                    ArrJson.encodeToString(rootFolders.first()),
                    body = ArrJson.encodeToJsonElement(rootFolders.first()),
                ) { r ->
                    assertEquals(
                        rootFolders.first(),
                        LidarrSystemApis.rootFolder(r)
                            .updateRootFolder("1", rootFolders.first()),
                    )
                },
                SettingsEndpointCase(
                    "deleteRootFolder", "DELETE", "api/v1/rootfolder/1",
                ) { r -> LidarrSystemApis.rootFolder(r).deleteRootFolder(1) },
                SettingsEndpointCase(
                    "getTags", "GET", "api/v1/tag",
                    settingsFixture("tag/getTags"),
                ) { r -> assertEquals(tags, LidarrSystemApis.tag(r).getTags()) },
                SettingsEndpointCase(
                    "getTag", "GET", "api/v1/tag/1",
                    ArrJson.encodeToString(tags.first()),
                ) { r -> assertEquals(tags.first(), LidarrSystemApis.tag(r).getTag(1)) },
                SettingsEndpointCase(
                    "addTag", "POST", "api/v1/tag",
                    ArrJson.encodeToString(tags.first()),
                    body = ArrJson.encodeToJsonElement(tags.first()),
                ) { r ->
                    assertEquals(tags.first(), LidarrSystemApis.tag(r).addTag(tags.first()))
                },
                SettingsEndpointCase(
                    "updateTag", "PUT", "api/v1/tag/1",
                    ArrJson.encodeToString(tags.first()),
                    body = ArrJson.encodeToJsonElement(tags.first()),
                ) { r ->
                    assertEquals(
                        tags.first(), LidarrSystemApis.tag(r).updateTag("1", tags.first())
                    )
                },
                SettingsEndpointCase(
                    "deleteTag", "DELETE", "api/v1/tag/1",
                ) { r -> LidarrSystemApis.tag(r).deleteTag(1) },
                SettingsEndpointCase(
                    "getTagDetails", "GET", "api/v1/tag/detail",
                    settingsFixture("tagdetails/getTagDetails"),
                ) { r ->
                    assertEquals(tagDetails, LidarrSystemApis.tagDetails(r).getTagDetails())
                },
                SettingsEndpointCase(
                    "getTagDetailsById", "GET", "api/v1/tag/detail/1",
                    ArrJson.encodeToString(tagDetails.first()),
                ) { r ->
                    assertEquals(
                        tagDetails.first(), LidarrSystemApis.tagDetails(r).getTagDetailsById(1)
                    )
                },
            ).map { case -> arrayOf<Any>(case) }
        }
    }
}
