package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the fake's in-memory semantics so UI tests can rely on them. */
class FakeRadarrConnectTest {
    private fun fake(
        latencyMs: Long = 0,
        errorFor: (String) -> Throwable? = { null },
    ) = FakeRadarrConnect(latencyMs = latencyMs, errorFor = errorFor, sleep = { })

    @Test fun downloadClientCrudFollowsServerSemantics() = runTest {
        val fake = fake()
        val created = fake.createDownloadClient(
            body = DownloadClientResource(name = "Sabnzbd", enable = true),
        )
        assertEquals(201, created.code())
        assertEquals(1, created.body()!!.id)
        assertEquals("Sabnzbd", fake.downloadClient(id = 1).body()!!.name)
        val updated = fake.updateDownloadClient(
            id = 1,
            body = DownloadClientResource(name = "Renamed"),
        )
        assertEquals(202, updated.code())
        assertEquals("Renamed", fake.downloadClients().single().name)
        fake.removeDownloadClient(id = 1)
        assertTrue(fake.downloadClients.isEmpty())
        assertEquals(404, fake.downloadClient(id = 1).code())
    }

    @Test fun downloadClientBulkMergesTagsAndSettings() = runTest {
        val fake = fake()
        fake.downloadClients += DownloadClientResource(id = 1, tags = setOf(2))
        fake.downloadClients += DownloadClientResource(id = 2, tags = emptySet())
        val result = fake.updateDownloadClientBulk(
            DownloadClientBulkResource(
                ids = listOf(1, 2),
                tags = listOf(5),
                applyTags = ApplyTags.ADD,
                enable = true,
            ),
        )
        assertEquals(200, result.code())
        assertEquals(setOf(2, 5), fake.downloadClients[0].tags)
        assertEquals(setOf(5), fake.downloadClients[1].tags)
        assertEquals(true, fake.downloadClients[0].enable)
        fake.removeDownloadClientBulk(DownloadClientBulkResource(ids = listOf(1)))
        assertEquals(listOf(2), fake.downloadClients.map { it.id })
    }

    @Test fun providerOperationsAreRecordedNotExecuted() = runTest {
        val fake = fake()
        fake.downloadClients += DownloadClientResource(id = 3, enable = true)
        fake.indexers += IndexerResource(id = 4, enableRss = true)
        fake.notifications += NotificationResource(id = 5)

        val client = DownloadClientResource(name = "Sabnzbd")
        assertEquals(204, fake.testDownloadClient(body = client).code())
        assertEquals(client, fake.testedDownloadClients.single())
        assertEquals(listOf(3), fake.testAllDownloadClients().body()!!.map { it.id })
        assertEquals(listOf(true), fake.testAllDownloadClients().body()!!.map { it.isValid })
        fake.downloadClientAction(name = "getCategories", body = client)
        assertEquals("getCategories" to client, fake.downloadClientActions.single())

        val indexer = IndexerResource(name = "Prowlarr")
        assertEquals(204, fake.testIndexer(body = indexer).code())
        assertEquals(indexer, fake.testedIndexers.single())
        assertEquals(listOf(4), fake.testAllIndexers().body()!!.map { it.id })
        fake.indexerAction(name = "getUrls", body = indexer)
        assertEquals("getUrls" to indexer, fake.indexerActions.single())

        val notification = NotificationResource(name = "Discord")
        assertEquals(204, fake.testNotification(body = notification).code())
        assertEquals(notification, fake.testedNotifications.single())
        assertEquals(listOf(5), fake.testAllNotifications().body()!!.map { it.id })
        fake.notificationAction(name = "channels", body = notification)
        assertEquals("channels" to notification, fake.notificationActions.single())
    }

    @Test fun indexerCrudAndBulkFollowServerSemantics() = runTest {
        val fake = fake()
        val created = fake.createIndexer(body = IndexerResource(name = "Prowlarr"))
        assertEquals(201, created.code())
        assertEquals(1, created.body()!!.id)
        assertEquals(202, fake.updateIndexer(
            id = 1,
            body = IndexerResource(name = "Renamed", priority = 25),
        ).code())
        assertEquals("Renamed", fake.indexer(id = 1).body()!!.name)
        val bulk = fake.updateIndexerBulk(
            IndexerBulkResource(
                ids = listOf(1),
                tags = listOf(7),
                applyTags = ApplyTags.REPLACE,
                enableAutomaticSearch = true,
            ),
        )
        assertEquals(200, bulk.code())
        assertEquals(setOf(7), fake.indexers.single().tags)
        assertEquals(true, fake.indexers.single().enableAutomaticSearch)
        fake.indexerSchemas += IndexerResource(id = 0, name = "Prowlarr")
        assertEquals("Prowlarr", fake.indexerSchema().single().name)
        fake.removeIndexerBulk(IndexerBulkResource(ids = listOf(1)))
        assertTrue(fake.indexers.isEmpty())
    }

    @Test fun notificationCrudFollowsServerSemantics() = runTest {
        val fake = fake()
        val created = fake.createNotification(
            body = NotificationResource(name = "Discord", onGrab = true),
        )
        assertEquals(201, created.code())
        assertEquals(1, created.body()!!.id)
        assertEquals(202, fake.updateNotification(
            id = 1,
            body = NotificationResource(name = "Renamed"),
        ).code())
        assertEquals("Renamed", fake.notification(id = 1).body()!!.name)
        fake.notificationSchemas += NotificationResource(id = 0, name = "Discord")
        assertEquals("Discord", fake.notificationSchema().single().name)
        fake.removeNotification(id = 1)
        assertTrue(fake.notifications.isEmpty())
        assertEquals(404, fake.notification(id = 1).code())
    }

    @Test fun configsRoundTrip() = runTest {
        val fake = fake()
        assertEquals(DownloadClientConfigResource(), fake.downloadClientConfig())
        val clientConfig = fake.updateDownloadClientConfig(
            id = "1",
            body = DownloadClientConfigResource(checkForFinishedDownloadInterval = 15),
        )
        assertEquals(202, clientConfig.code())
        assertEquals(15, fake.downloadClientConfig().checkForFinishedDownloadInterval)
        assertEquals(200, fake.downloadClientConfigById(id = 1).code())
        assertEquals(404, fake.downloadClientConfigById(id = 9).code())

        assertEquals(IndexerConfigResource(), fake.indexerConfig())
        val indexerConfig = fake.updateIndexerConfig(
            id = "1",
            body = IndexerConfigResource(minimumAge = 60),
        )
        assertEquals(202, indexerConfig.code())
        assertEquals(60, fake.indexerConfig().minimumAge)
        assertEquals(200, fake.indexerConfigById(id = 1).code())
        assertEquals(404, fake.indexerConfigById(id = 9).code())
    }

    @Test fun indexerFlagsReturnConfiguredList() = runTest {
        val fake = fake()
        fake.indexerFlags += IndexerFlagResource(id = 1, name = "g_freeleech")
        assertEquals(1, fake.indexerFlags().single().id)
    }

    @Test fun remotePathMappingCrudFollowsServerSemantics() = runTest {
        val fake = fake()
        val created = fake.createRemotePathMapping(
            body = RemotePathMappingResource(host = "nas", remotePath = "/remote/"),
        )
        assertEquals(201, created.code())
        assertEquals(1, created.body()!!.id)
        assertEquals(200, fake.updateRemotePathMapping(
            id = "1",
            body = RemotePathMappingResource(host = "nas", localPath = "/local/"),
        ).code())
        assertEquals("/local/", fake.remotePathMapping(id = 1).body()!!.localPath)
        fake.removeRemotePathMapping(id = 1)
        assertTrue(fake.remotePathMappings.isEmpty())
        assertEquals(404, fake.remotePathMapping(id = 1).code())
    }

    @Test fun errorInjectionRunsBeforeMutation() = runTest {
        val fake = fake(errorFor = { IOException("offline") })
        try {
            fake.downloadClients()
            throw AssertionError("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        assertTrue(fake.downloadClients.isEmpty())
    }

    @Test fun latencySleepsBeforeResponding() = runTest {
        var slept = 0L
        val fake = FakeRadarrConnect(latencyMs = 25, sleep = { slept += it })
        fake.notifications()
        assertEquals(25, slept)
    }

}
