package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import dev.appolon.homelab.arr.api.sonarr.model.RemotePathMappingResource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeSonarrConnectTest {
    @Test
    fun providerLifecycle() = runTest {
        val fake = FakeSonarrConnect()
        fake.downloadClientStore[1] = DownloadClientResource(id = 1, name = "SABnzbd")
        fake.downloadClientStore[2] = DownloadClientResource(id = 2, name = "qBittorrent")
        assertEquals(2, fake.downloadClientApi.list().size)
        val created = fake.downloadClientApi.create(
            forceSave = true, body = DownloadClientResource(name = "NZBGet")
        )
        assertEquals(3, created.id)
        assertEquals(1, fake.forcedSaves)
        val updated = fake.downloadClientApi.update(
            id = 1, forceSave = null, body = DownloadClientResource(name = "SABnzbd 2")
        )
        assertEquals("SABnzbd 2", fake.downloadClientStore[1]!!.name)
        assertEquals(1, updated.id)
        assertEquals(200, fake.downloadClientApi.get(1).code())
        assertEquals(404, fake.downloadClientApi.get(99).code())
        val bulk = fake.downloadClientApi.updateBulk(
            DownloadClientBulkResource(
                ids = listOf(1, 2), enable = true, tags = listOf(5), applyTags = ApplyTags.ADD
            )
        )
        assertTrue(bulk.enable)
        assertEquals(setOf(5), fake.downloadClientStore[2]!!.tags)
        fake.downloadClientApi.deleteBulk(DownloadClientBulkResource(ids = listOf(2)))
        assertEquals(setOf(1, 3), fake.downloadClientStore.keys)
        fake.downloadClientSchemas.add(DownloadClientResource(implementationName = "Sabnzbd"))
        assertEquals(1, fake.downloadClientApi.schema().size)
        fake.downloadClientApi.test(
            forceTest = true, body = DownloadClientResource(name = "NZBGet")
        )
        assertEquals(1, fake.forcedTests)
        assertEquals(listOf("NZBGet"), fake.testedDownloadClients.map { it.name })
        fake.downloadClientApi.testAll()
        assertEquals(1, fake.testedAllCounts.size)
        fake.downloadClientApi.action("getRetention", DownloadClientResource(name = "NZBGet"))
        assertEquals(1, fake.actionCalls["DownloadClient.getRetention"]!!.size)
        fake.downloadClientApi.delete(1)
        assertTrue(1 !in fake.downloadClientStore)
    }

    @Test
    fun indexerLifecycleAndFlags() = runTest {
        val fake = FakeSonarrConnect()
        fake.indexerStore[1] = IndexerResource(id = 1, name = "NZBgeek", priority = 25)
        fake.indexerStore[2] = IndexerResource(id = 2, name = "IPTorrents", tags = setOf(2))
        assertEquals(2, fake.indexerApi.list().size)
        val bulk = fake.indexerApi.updateBulk(
            IndexerBulkResource(
                ids = listOf(1, 2), enableRss = false,
                tags = listOf(2), applyTags = ApplyTags.REMOVE
            )
        )
        assertEquals(2, bulk.id)
        assertEquals(25, fake.indexerStore[1]!!.priority)
        assertEquals(false, fake.indexerStore[1]!!.enableRss)
        assertEquals(emptySet<Int>(), fake.indexerStore[2]!!.tags)
        fake.indexerSchemas.add(IndexerResource(implementationName = "Newznab"))
        assertEquals(1, fake.indexerApi.schema().size)
        fake.indexerApi.test(forceTest = null, body = IndexerResource(name = "NZBgeek"))
        assertEquals(listOf("NZBgeek"), fake.testedIndexers.map { it.name })
        fake.indexerApi.testAll()
        fake.indexerApi.action("getUrls", IndexerResource(name = "NZBgeek"))
        assertTrue("Indexer.getUrls" in fake.actionCalls)
        fake.indexerFlags.addAll(
            listOf(IndexerFlagResource(id = 1, name = "g"), IndexerFlagResource(id = 2))
        )
        assertEquals(2, fake.indexerFlagApi.list().size)
        fake.indexerConfig = IndexerConfigResource(id = 1, rssSyncInterval = 30)
        assertEquals(30, fake.indexerConfigApi.get().rssSyncInterval)
        assertEquals(200, fake.indexerConfigApi.get(1).code())
        assertEquals(404, fake.indexerConfigApi.get(9).code())
        fake.indexerConfigApi.update("1", IndexerConfigResource(minimumAge = 5))
        assertEquals(5, fake.indexerConfig.minimumAge)
    }

    @Test
    fun notificationAndRemotePathMappingLifecycle() = runTest {
        val fake = FakeSonarrConnect()
        fake.notificationStore[1] = NotificationResource(id = 1, name = "Webhook")
        assertEquals(1, fake.notificationApi.list().size)
        val created = fake.notificationApi.create(
            body = NotificationResource(name = "Discord", onGrab = true)
        )
        assertEquals(2, created.id)
        fake.notificationApi.update(1, forceSave = null, body = NotificationResource(name = "Hook"))
        assertEquals("Hook", fake.notificationStore[1]!!.name)
        assertEquals(200, fake.notificationApi.get(1).code())
        assertEquals(404, fake.notificationApi.get(9).code())
        fake.notificationSchemas.add(NotificationResource(implementationName = "Webhook"))
        assertEquals(1, fake.notificationApi.schema().size)
        fake.notificationApi.test(forceTest = false, body = NotificationResource(name = "Hook"))
        assertEquals(listOf("Hook"), fake.testedNotifications.map { it.name })
        fake.notificationApi.testAll()
        fake.notificationApi.action("test", NotificationResource(name = "Hook"))
        assertEquals(1, fake.actionCalls["Notification.test"]!!.size)
        fake.notificationApi.delete(1)
        assertEquals(setOf(2), fake.notificationStore.keys)
        fake.downloadClientConfig = DownloadClientConfigResource(
            id = 1, autoRedownloadFailed = true
        )
        assertEquals(true, fake.downloadClientConfigApi.get().autoRedownloadFailed)
        fake.downloadClientConfigApi.update("1", DownloadClientConfigResource())
        assertEquals(false, fake.downloadClientConfig.autoRedownloadFailed)
        val mapping = fake.remotePathMappingApi.create(
            body = RemotePathMappingResource(host = "media-server", remotePath = "/tv")
        )
        assertEquals(1, mapping.id)
        fake.remotePathMappingApi.update("1", RemotePathMappingResource(localPath = "/data/tv"))
        assertEquals("/data/tv", fake.remotePathMappingStore[1]!!.localPath)
        assertEquals(200, fake.remotePathMappingApi.get(1).code())
        assertEquals(1, fake.remotePathMappingApi.list().size)
        fake.remotePathMappingApi.delete(1)
        assertTrue(fake.remotePathMappingStore.isEmpty())
    }

    @Test
    fun latencyRunsBeforeErrorInjection() = runTest {
        val operations = mutableListOf<String>()
        val fake = FakeSonarrConnect(
            latency = { operations.add(it) },
            error = { if (it == "Indexer.list") IOException("offline") else null }
        )
        try {
            fake.indexerApi.list()
            fail("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        assertEquals(0, fake.downloadClientApi.list().size)
        assertEquals(listOf("Indexer.list", "DownloadClient.list"), operations)
    }
}
