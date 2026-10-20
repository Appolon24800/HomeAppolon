package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApiInfoResource
import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.api.sonarr.model.UiConfigResource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSonarrSystemTest {
    @Test
    fun tagCrudRoundTrip() = runTest {
        val fake = FakeSonarrSystem()
        fake.tagApi.create(TagResource(label = "anime"))
        fake.tagApi.create(TagResource(label = "4k"))
        assertEquals(listOf(1, 2), fake.tagStore.keys.toList())
        fake.tagApi.update(id = "2", body = TagResource(id = 2, label = "uhd"))
        assertEquals("uhd", fake.tagStore[2]!!.label)
        fake.tagApi.delete(1)
        assertEquals(listOf(2), fake.tagStore.keys.toList())
    }

    @Test
    fun getReturns404ResponseForMissingTag() = runTest {
        val fake = FakeSonarrSystem()
        val response = fake.tagApi.get(7)
        assertEquals(404, response.code())
        assertNull(response.body())
    }

    @Test
    fun errorInjectionThrowsBeforeStateChanges() = runTest {
        val fake = FakeSonarrSystem(error = { if (it == "Tag.create") IOException("offline") else null })
        try {
            fake.tagApi.create(TagResource(label = "anime"))
            throw AssertionError("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        assertTrue(fake.tagStore.isEmpty())
    }

    @Test
    fun latencyCallbackReceivesTagAndMethod() = runTest {
        val calls = mutableListOf<String>()
        val fake = FakeSonarrSystem(latency = { calls.add(it) })
        fake.systemApi.restart()
        fake.systemApi.shutdown()
        fake.healthApi.list()
        assertEquals(listOf("System.restart", "System.shutdown", "Health.list"), calls)
    }

    @Test
    fun countersTrackSystemActions() = runTest {
        val fake = FakeSonarrSystem()
        fake.systemApi.restart()
        fake.systemApi.restart()
        fake.systemApi.shutdown()
        fake.backupApi.restore(3)
        fake.backupApi.restoreUpload()
        assertEquals(2, fake.restartCount)
        assertEquals(1, fake.shutdownCount)
        assertEquals(listOf(3), fake.restoredBackupIds)
        assertEquals(1, fake.restoreUploadCount)
    }

    @Test
    fun configStoresUpdate() = runTest {
        val fake = FakeSonarrSystem()
        fake.hostConfigApi.update(
            id = "1", body = HostConfigResource(id = 1, port = 8989, launchBrowser = true)
        )
        fake.uiConfigApi.update(id = "1", body = UiConfigResource(id = 1, theme = "dark"))
        assertEquals(8989, fake.hostConfig.port)
        assertEquals(true, fake.hostConfig.launchBrowser)
        assertEquals("dark", fake.uiConfig.theme)
    }

    @Test
    fun autoTaggingAndMetadataStoresGrow() = runTest {
        val fake = FakeSonarrSystem()
        fake.autoTaggingApi.create(AutoTaggingResource(name = "Anime tagging", tags = listOf(1)))
        fake.metadataApi.create(body = MetadataResource(name = "Kodi"))
        assertEquals(1, fake.autoTaggingStore[1]!!.id)
        assertEquals("Kodi", fake.metadataStore[1]!!.name)
        fake.metadataApi.test(body = MetadataResource(name = "Kodi"))
        assertEquals(1, fake.testedMetadata.size)
    }

    @Test
    fun authenticationRecordsCalls() = runTest {
        val fake = FakeSonarrSystem()
        fake.authenticationApi.sso(returnUrl = "/sonarr/series")
        fake.authenticationApi.login(
            returnUrl = null,
            body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username", "user")
                .build()
        )
        fake.authenticationApi.logout()
        assertEquals(
            listOf("sso:/sonarr/series", "login:null", "logout"),
            fake.authenticationCalls
        )
    }

    @Test
    fun simpleGettersReturnSeededState() = runTest {
        val fake = FakeSonarrSystem()
        fake.apiInfo = ApiInfoResource(current = "v3", deprecated = listOf("v1"))
        fake.backups.add(BackupResource(id = 1, name = "sonarr_backup_v4"))
        assertEquals("v3", fake.apiInfoApi.get().current)
        assertEquals(listOf("sonarr_backup_v4"), fake.backupApi.list().map { it.name!! })
    }
}
