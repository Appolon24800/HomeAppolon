package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.lidarr.model.BackupResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.lidarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.LogResource
import dev.appolon.homelab.arr.api.lidarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FakeLidarrSystemTest {
    @Test
    fun tagCrudUsesStringUpdateId() = runTest {
        val fake = FakeLidarrSystem()
        val created = fake.addTag(TagResource(label = "example"))
        assertEquals(1, created.id)
        assertEquals(listOf(created), fake.getTags())

        val updated = fake.updateTag("1", created.copy(label = "renamed"))
        assertEquals("renamed", updated.label)
        assertEquals("renamed", fake.getTag(1).label)

        fake.deleteTag(1)
        assertTrue(fake.tags.isEmpty())
    }

    @Test
    fun rootFolderAndRemotePathMappingCrud() = runTest {
        val fake = FakeLidarrSystem()
        val folder = fake.addRootFolder(RootFolderResource(name = "Music", path = "/music"))
        assertEquals(1, folder.id)
        assertEquals(folder, fake.getRootFolder(1))
        assertEquals("Audio", fake.updateRootFolder("1", folder.copy(name = "Audio")).name)

        val mapping = fake.addRemotePathMapping(
            RemotePathMappingResource(host = "seedbox", remotePath = "/downloads")
        )
        assertEquals(listOf(mapping), fake.getRemotePathMappings())
        fake.deleteRemotePathMapping(mapping.id)
        assertTrue(fake.remotePathMappings.isEmpty())
    }

    @Test
    fun autoTaggingCrudUsesStringUpdateId() = runTest {
        val fake = FakeLidarrSystem()
        val created = fake.addAutoTagging(AutoTaggingResource(name = "Fresh", tags = setOf(1)))
        assertEquals(1, created.id)
        assertEquals(created, fake.getAutoTagging(1))
        assertEquals("Renamed", fake.updateAutoTagging("1", created.copy(name = "Renamed")).name)
        fake.deleteAutoTagging(1)
        assertTrue(fake.autoTaggings.isEmpty())
    }

    @Test
    fun downloadClientBulkMergesTagsAndFields() = runTest {
        val fake = FakeLidarrSystem()
        fake.downloadClients[1] =
            DownloadClientResource(id = 1, name = "One", tags = setOf(1), priority = 25)
        fake.downloadClients[2] =
            DownloadClientResource(id = 2, name = "Two", tags = setOf(3), priority = 50)

        val merged = fake.updateDownloadClients(
            DownloadClientBulkResource(
                ids = listOf(1, 2), tags = listOf(2), applyTags = ApplyTags.ADD, priority = 10,
            )
        )
        // The spec types the bulk response as one resource; the fake returns the last id.
        assertEquals(2, merged.id)
        assertEquals(setOf(1, 2), fake.getDownloadClient(1).tags)
        assertEquals(setOf(2, 3), fake.getDownloadClient(2).tags)
        assertEquals(10, fake.getDownloadClient(1).priority)

        fake.testDownloadClient(resource = fake.getDownloadClient(1))
        fake.testAllDownloadClients()
        fake.downloadClientAction("search", fake.getDownloadClient(1))
        assertEquals(1, fake.testAllDownloadClientsCount)
        assertEquals(1, fake.testedDownloadClients.size)
        assertEquals(1, fake.downloadClientActions["search"]?.size)

        fake.deleteDownloadClients(DownloadClientBulkResource(ids = listOf(1, 2)))
        assertTrue(fake.downloadClients.isEmpty())
    }

    @Test
    fun configsRoundTripThroughSingletons() = runTest {
        val fake = FakeLidarrSystem()
        fake.updateHostConfig("1", HostConfigResource(port = 1234, branch = "develop"))
        assertEquals(1234, fake.getHostConfig().port)
        assertEquals(1234, fake.getHostConfigById(1).port)
        assertEquals("develop", fake.getHostConfig().branch)

        fake.mediaManagementConfig = fake.mediaManagementConfig.copy(recycleBin = "/tmp")
        assertEquals("/tmp", fake.getMediaManagementConfig().recycleBin)
        assertEquals("/tmp", fake.getMediaManagementConfigById(1).recycleBin)
    }

    @Test
    fun logPagesFilterAndSort() = runTest {
        val fake = FakeLidarrSystem()
        fake.logRecords += listOf(
            LogResource(id = 1, time = "2026-10-01T12:00:00Z", level = "Info"),
            LogResource(id = 2, time = "2026-10-01T13:00:00Z", level = "Error"),
            LogResource(id = 3, time = "2026-10-01T14:00:00Z", level = "Error"),
        )

        val all = fake.getLog()
        assertEquals(3, all.totalRecords)

        val errors = fake.getLog(level = "error")
        assertEquals(listOf(2, 3), errors.records!!.map { it.id })

        val newestFirst = fake.getLog(sortKey = "time", sortDirection = SortDirection.DESCENDING)
        assertEquals(listOf(3, 2, 1), newestFirst.records!!.map { it.id })

        val window = fake.getLog(page = 2, pageSize = 2)
        assertEquals(listOf(3), window.records!!.map { it.id })
    }

    @Test
    fun logFileContentStreamsSeededText() = runTest {
        val fake = FakeLidarrSystem()
        fake.logFileContents["lidarr.txt"] = "line one\n"
        assertEquals("line one\n", fake.getLogFile("lidarr.txt").body()!!.string())
        assertEquals(404, fake.getLogFile("missing.txt").code())
    }

    @Test
    fun countersTrackDestructiveOperations() = runTest {
        val fake = FakeLidarrSystem()
        fake.backups[1] = BackupResource(id = 1, name = "backup.zip")
        fake.restoreBackup(1)
        fake.restoreUploadedBackup()
        fake.restart()
        fake.shutdown()
        assertEquals(listOf(1), fake.restoredBackupIds)
        assertEquals(1, fake.restoreUploadCount)
        assertEquals(1, fake.restartCount)
        assertEquals(1, fake.shutdownCount)
    }

    @Test
    fun unknownIdsReturn404() = runTest {
        val fake = FakeLidarrSystem()
        val errors = listOf<suspend () -> Unit>(
            { fake.getTag(9) },
            { fake.getTagDetailsById(9) },
            { fake.getRootFolder(9) },
            { fake.getRemotePathMapping(9) },
            { fake.getAutoTagging(9) },
            { fake.getTask(9) },
            { fake.deleteBackup(9) },
            { fake.restoreBackup(9) },
            { fake.getMetadata(9) },
            { fake.getNotification(9) },
            { fake.getDownloadClient(9) },
            { fake.getIndexer(9) },
            { fake.updateTag("not-a-number", TagResource(label = "x")) },
        )
        errors.forEach { body ->
            var caught: HttpException? = null
            try {
                body()
            } catch (error: HttpException) {
                caught = error
            }
            assertEquals(404, caught?.code())
        }
    }

    @Test
    fun latencyAndErrorInjectionRunPerOperation() = runTest {
        val operations = mutableListOf<String>()
        val fake = FakeLidarrSystem(
            latency = { operations += it },
            error = { operation ->
                if (operation == "getHealth") HttpException(
                    Response.error<Unit>(500, "".toResponseBody())
                ) else null
            }
        )
        fake.getTags()
        assertEquals(listOf("getTags"), operations)

        var caught: HttpException? = null
        try {
            fake.getHealth()
        } catch (error: HttpException) {
            caught = error
        }
        assertEquals(500, caught?.code())
        assertEquals(listOf("getTags", "getHealth"), operations)
    }

    @Test
    fun rawJsonEndpointsServeSeededPayloads() = runTest {
        val fake = FakeLidarrSystem()
        assertNull(fake.getApiInfo()["missing"])
        assertTrue(fake.getRoutes().containsKey("routes"))
        assertTrue(fake.getDuplicateRoutes().toString() == "[]")
        assertTrue(fake.getFileSystemType().toString().contains("folder"))
    }
}
