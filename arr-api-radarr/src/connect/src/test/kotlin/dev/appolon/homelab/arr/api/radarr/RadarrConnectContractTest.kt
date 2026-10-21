package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each spec operation has its own fixture and verifies the base-path prefix. */
class RadarrConnectContractTest {
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use { it.readText() }

    @Test fun downloadClientsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "downloadClients")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            val result = api.downloadClients()
            assertEquals("Sabnzbd", result.single().name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/downloadclient", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createDownloadClientHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("downloadclient", "createDownloadClient")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            val body = DownloadClientResource(name = "Sabnzbd")
            val result = api.createDownloadClient(forceSave = true, body = body)
            assertEquals(201, result.code())
            assertEquals("Sabnzbd", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/downloadclient", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceSave"))
            assertEquals(
                ArrJson.encodeToString(DownloadClientResource.serializer(), body),
                request.body.readUtf8(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateDownloadClientHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("downloadclient", "updateDownloadClient")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            val result = api.updateDownloadClient(
                id = 1,
                forceSave = false,
                body = DownloadClientResource(name = "Sabnzbd"),
            )
            assertEquals(202, result.code())
            assertEquals(1, result.body()!!.priority)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/downloadclient/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeDownloadClientHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "removeDownloadClient")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            assertTrue(api.removeDownloadClient(id = 1).isEmpty())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/downloadclient/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "downloadClient")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            assertEquals("Sabnzbd", api.downloadClient(id = 1).body()!!.name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/downloadclient/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateDownloadClientBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "updateDownloadClientBulk")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            val result = api.updateDownloadClientBulk(
                DownloadClientBulkResource(
                    ids = listOf(1),
                    tags = listOf(5),
                    applyTags = ApplyTags.ADD,
                    priority = 2,
                ),
            )
            assertEquals(200, result.code())
            assertEquals(2, result.body()!!.single().priority)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/downloadclient/bulk", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeDownloadClientBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "removeDownloadClientBulk")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            assertTrue(
                api.removeDownloadClientBulk(DownloadClientBulkResource(ids = listOf(1))).isEmpty(),
            )
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/downloadclient/bulk", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientSchemaHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "downloadClientSchema")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            val schema = api.downloadClientSchema().single()
            assertEquals("Sabnzbd", schema.name)
            val select = schema.fields!!.single { it.name == "category" }
            assertEquals(2, select.selectOptions!!.size)
            assertEquals("visible", select.hidden)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/downloadclient/schema", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun testDownloadClientHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            assertEquals(204, api.testDownloadClient(
                forceTest = true,
                body = DownloadClientResource(name = "Sabnzbd"),
            ).code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/downloadclient/test", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceTest"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun testAllDownloadClientsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclient", "testAllDownloadClients")))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            assertEquals(1, api.testAllDownloadClients().body()!!.single().id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/downloadclient/testall", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientActionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            assertEquals(200, api.downloadClientAction(
                name = "getCategories",
                body = DownloadClientResource(name = "Sabnzbd"),
            ).code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(
                "/radarr/api/v3/downloadclient/action/getCategories",
                request.requestUrl!!.encodedPath,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclientconfig", "downloadClientConfig")))
            val api = RadarrConnectApis.downloadClientConfig(retrofit(server))
            assertEquals("string", api.downloadClientConfig().downloadClientWorkingFolders)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(
                "/radarr/api/v3/config/downloadclient",
                request.requestUrl!!.encodedPath,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateDownloadClientConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("downloadclientconfig", "updateDownloadClientConfig")))
            val api = RadarrConnectApis.downloadClientConfig(retrofit(server))
            val result = api.updateDownloadClientConfig(
                id = "1",
                body = DownloadClientConfigResource(id = 1),
            )
            assertEquals(202, result.code())
            assertEquals(1, result.body()!!.id)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals(
                "/radarr/api/v3/config/downloadclient/1",
                request.requestUrl!!.encodedPath,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("downloadclientconfig", "downloadClientConfigById")))
            val api = RadarrConnectApis.downloadClientConfig(retrofit(server))
            assertEquals(1, api.downloadClientConfigById(id = 1).body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(
                "/radarr/api/v3/config/downloadclient/1",
                request.requestUrl!!.encodedPath,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexersHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "indexers")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            val result = api.indexers()
            assertEquals("Prowlarr", result.single().name)
            assertEquals(true, result.single().enableRss)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/indexer", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createIndexerHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("indexer", "createIndexer")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            val result = api.createIndexer(
                forceSave = true,
                body = IndexerResource(name = "Prowlarr"),
            )
            assertEquals(201, result.code())
            assertEquals("Prowlarr", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/indexer", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceSave"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateIndexerHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("indexer", "updateIndexer")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            val result = api.updateIndexer(
                id = 1,
                forceSave = false,
                body = IndexerResource(name = "Prowlarr"),
            )
            assertEquals(202, result.code())
            assertEquals(25, result.body()!!.priority)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/indexer/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeIndexerHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "removeIndexer")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            assertTrue(api.removeIndexer(id = 1).isEmpty())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/indexer/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "indexer")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            assertEquals("Prowlarr", api.indexer(id = 1).body()!!.name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/indexer/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateIndexerBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "updateIndexerBulk")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            val result = api.updateIndexerBulk(
                IndexerBulkResource(
                    ids = listOf(1),
                    tags = listOf(5),
                    applyTags = ApplyTags.REPLACE,
                    priority = 25,
                ),
            )
            assertEquals(200, result.code())
            assertEquals(25, result.body()!!.single().priority)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/indexer/bulk", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeIndexerBulkHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "removeIndexerBulk")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            assertTrue(api.removeIndexerBulk(IndexerBulkResource(ids = listOf(1))).isEmpty())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/indexer/bulk", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerSchemaHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "indexerSchema")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            val schema = api.indexerSchema().single()
            assertEquals("Prowlarr", schema.name)
            assertEquals(true, schema.supportsSearch)
            assertEquals(2, schema.fields!!.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/indexer/schema", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun testIndexerHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val api = RadarrConnectApis.indexer(retrofit(server))
            assertEquals(204, api.testIndexer(
                forceTest = true,
                body = IndexerResource(name = "Prowlarr"),
            ).code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/indexer/test", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceTest"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun testAllIndexersHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexer", "testAllIndexers")))
            val api = RadarrConnectApis.indexer(retrofit(server))
            assertEquals(1, api.testAllIndexers().body()!!.single().id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/indexer/testall", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerActionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrConnectApis.indexer(retrofit(server))
            assertEquals(200, api.indexerAction(
                name = "getUrls",
                body = IndexerResource(name = "Prowlarr"),
            ).code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(
                "/radarr/api/v3/indexer/action/getUrls",
                request.requestUrl!!.encodedPath,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexerconfig", "indexerConfig")))
            val api = RadarrConnectApis.indexerConfig(retrofit(server))
            assertEquals(1, api.indexerConfig().id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/indexer", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateIndexerConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("indexerconfig", "updateIndexerConfig")))
            val api = RadarrConnectApis.indexerConfig(retrofit(server))
            val result = api.updateIndexerConfig(
                id = "1",
                body = IndexerConfigResource(id = 1, minimumAge = 60),
            )
            assertEquals(202, result.code())
            assertEquals(60, result.body()!!.minimumAge)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/indexer/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexerconfig", "indexerConfigById")))
            val api = RadarrConnectApis.indexerConfig(retrofit(server))
            assertEquals(1, api.indexerConfigById(id = 1).body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/indexer/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerFlagsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("indexerflag", "indexerFlags")))
            val api = RadarrConnectApis.indexerFlag(retrofit(server))
            val flags = api.indexerFlags()
            assertEquals(2, flags.size)
            assertEquals("g_freeleech", flags[0].name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/indexerflag", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun notificationsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("notification", "notifications")))
            val api = RadarrConnectApis.notification(retrofit(server))
            val result = api.notifications()
            assertEquals("Discord", result.single().name)
            assertEquals(true, result.single().onGrab)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/notification", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createNotificationHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("notification", "createNotification")))
            val api = RadarrConnectApis.notification(retrofit(server))
            val result = api.createNotification(
                forceSave = true,
                body = NotificationResource(name = "Discord"),
            )
            assertEquals(201, result.code())
            assertEquals("Discord", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/notification", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceSave"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateNotificationHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("notification", "updateNotification")))
            val api = RadarrConnectApis.notification(retrofit(server))
            val result = api.updateNotification(
                id = 1,
                forceSave = false,
                body = NotificationResource(name = "Discord"),
            )
            assertEquals(202, result.code())
            assertEquals(true, result.body()!!.onDownload)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/notification/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeNotificationHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("notification", "removeNotification")))
            val api = RadarrConnectApis.notification(retrofit(server))
            assertTrue(api.removeNotification(id = 1).isEmpty())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/notification/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun notificationHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("notification", "notification")))
            val api = RadarrConnectApis.notification(retrofit(server))
            assertEquals("Discord", api.notification(id = 1).body()!!.name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/notification/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun notificationSchemaHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("notification", "notificationSchema")))
            val api = RadarrConnectApis.notification(retrofit(server))
            val schema = api.notificationSchema().single()
            assertEquals("Discord", schema.name)
            assertEquals(true, schema.supportsOnGrab)
            assertEquals("string", schema.link)
            assertEquals(1, schema.fields!!.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/notification/schema", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun testNotificationHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val api = RadarrConnectApis.notification(retrofit(server))
            assertEquals(204, api.testNotification(
                forceTest = true,
                body = NotificationResource(name = "Discord"),
            ).code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/notification/test", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"),
                request.requestUrl!!.queryParameterValues("forceTest"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun testAllNotificationsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("notification", "testAllNotifications")))
            val api = RadarrConnectApis.notification(retrofit(server))
            assertEquals(1, api.testAllNotifications().body()!!.single().id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/notification/testall", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun notificationActionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrConnectApis.notification(retrofit(server))
            assertEquals(200, api.notificationAction(
                name = "channels",
                body = NotificationResource(name = "Discord"),
            ).code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(
                "/radarr/api/v3/notification/action/channels",
                request.requestUrl!!.encodedPath,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test fun remotePathMappingsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("remotepathmapping", "remotePathMappings")))
            val api = RadarrConnectApis.remotePathMapping(retrofit(server))
            assertEquals("/remote/", api.remotePathMappings().single().remotePath)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/remotepathmapping", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createRemotePathMappingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("remotepathmapping", "createRemotePathMapping")))
            val api = RadarrConnectApis.remotePathMapping(retrofit(server))
            val result = api.createRemotePathMapping(
                body = RemotePathMappingResource(host = "string"),
            )
            assertEquals(201, result.code())
            assertEquals("/local/", result.body()!!.localPath)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/remotepathmapping", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeRemotePathMappingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrConnectApis.remotePathMapping(retrofit(server))
            api.removeRemotePathMapping(id = 1)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/remotepathmapping/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateRemotePathMappingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("remotepathmapping", "updateRemotePathMapping")))
            val api = RadarrConnectApis.remotePathMapping(retrofit(server))
            val result = api.updateRemotePathMapping(
                id = "1",
                body = RemotePathMappingResource(host = "string"),
            )
            assertEquals(200, result.code())
            assertEquals(1, result.body()!!.id)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/remotepathmapping/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun remotePathMappingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("remotepathmapping", "remotePathMapping")))
            val api = RadarrConnectApis.remotePathMapping(retrofit(server))
            assertEquals(1, api.remotePathMapping(id = 1).body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/remotepathmapping/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.downloadClient(retrofit(server))
            try {
                api.downloadClients()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun downloadClientConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.downloadClientConfig(retrofit(server))
            try {
                api.downloadClientConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.indexer(retrofit(server))
            try {
                api.indexers()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.indexerConfig(retrofit(server))
            try {
                api.indexerConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun indexerFlagErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.indexerFlag(retrofit(server))
            try {
                api.indexerFlags()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun notificationErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.notification(retrofit(server))
            try {
                api.notifications()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun remotePathMappingErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrConnectApis.remotePathMapping(retrofit(server))
            try {
                api.remotePathMappings()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

}
