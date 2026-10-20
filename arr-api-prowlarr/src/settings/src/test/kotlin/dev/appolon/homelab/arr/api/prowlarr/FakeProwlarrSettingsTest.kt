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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** Drives the fake the way UI tests will: seeded state, CRUD, injections. */
class FakeProwlarrSettingsTest {
    @Test
    fun downloadClientCrudAndBulkTagMerge() = runTest {
        val fake = FakeProwlarrSettings()
        fake.downloadClients[1] = DownloadClientResource(id = 1, name = "One", tags = listOf(2))
        fake.downloadClients[2] = DownloadClientResource(id = 2, name = "Two", tags = listOf(2))

        assertEquals(2, fake.getDownloadClients().size)
        assertEquals("One", fake.getDownloadClient(1).name)

        val created = fake.createDownloadClient(null, DownloadClientResource(name = "Three"))
        assertTrue(created.id != 0)
        assertEquals(3, fake.downloadClients.size)

        val updated = fake.updateDownloadClient(
            "1", null, DownloadClientResource(id = 1, name = "Renamed")
        )
        assertEquals("Renamed", updated.name)
        assertEquals("Renamed", fake.downloadClients.getValue(1).name)

        val bulk = fake.updateDownloadClients(
            DownloadClientBulkResource(
                ids = listOf(1, 2),
                tags = listOf(7),
                applyTags = ApplyTags.ADD,
                enable = false,
                priority = 60,
            )
        )
        assertEquals(false, bulk.enable)
        // The rename upsert replaced client 1 wholesale, so its old tags are
        // gone before the merge; client 2 kept its seed tags.
        assertEquals(listOf(7), fake.downloadClients.getValue(1).tags)
        assertEquals(listOf(2, 7), fake.downloadClients.getValue(2).tags)
        assertEquals(60, fake.downloadClients.getValue(2).priority)

        assertTrue(fake.deleteDownloadClient(2).isSuccessful)
        assertEquals(listOf(1, created.id), fake.downloadClients.keys.sorted())

        val bulkDelete = fake.deleteDownloadClients(
            DownloadClientBulkResource(ids = listOf(1))
        )
        assertTrue(bulkDelete.isSuccessful)
        assertEquals(listOf(created.id), fake.downloadClients.keys.sorted())
    }

    @Test
    fun singletonConfigsRoundTripIgnoringPathId() = runTest {
        val fake = FakeProwlarrSettings()
        val host = HostConfigResource(port = 9696)
        fake.hostConfig = host
        assertEquals(host, fake.getHostConfig())
        assertEquals(host, fake.getHostConfig(99))

        val savedHost = fake.updateHostConfig("1", host.copy(port = 9697))
        assertEquals(9697, savedHost.port)
        assertEquals(9697, fake.getHostConfig().port)

        val development = DevelopmentConfigResource(logSql = true)
        fake.updateDevelopmentConfig("1", development)
        assertEquals(development, fake.getDevelopmentConfig())
        assertEquals(development, fake.getDevelopmentConfig(42))

        val ui = UiConfigResource(uiLanguage = "fr", theme = "light")
        fake.updateUiConfig("1", ui)
        assertEquals(ui, fake.getUiConfig())
        assertEquals(ui, fake.getUiConfig(42))

        fake.updateDownloadClientConfig("1", DownloadClientConfigResource())
        assertEquals(1, fake.getDownloadClientConfig().id)
    }

    @Test
    fun notificationCrudSchemaAndUnknown404() = runTest {
        val fake = FakeProwlarrSettings()
        fake.notificationSchemas += NotificationResource(id = 0, name = "Discord")
        val created = fake.createNotification(null, NotificationResource(name = "Spec"))
        assertEquals("Spec", fake.getNotification(created.id).name)
        assertEquals(1, fake.getNotificationSchemas().size)
        assertEquals(1, fake.getNotifications().size)

        fake.updateNotification(
            created.id.toString(), null, NotificationResource(id = created.id, name = "Renamed")
        )
        assertEquals("Renamed", fake.notifications.getValue(created.id).name)

        assertTrue(fake.testNotification(true, created).isSuccessful)
        assertTrue(fake.testAllNotifications().isSuccessful)
        assertTrue(fake.notificationAction("specAction", created).isSuccessful)

        assertTrue(fake.deleteNotification(created.id).isSuccessful)
        val error = runCatching { fake.getNotification(created.id) }.exceptionOrNull()
        assertTrue(error is HttpException)
        assertEquals(404, (error as HttpException).code())
    }

    @Test
    fun authLoginLogoutRecordsFormValues() = runTest {
        val fake = FakeProwlarrSettings()
        assertFalse(fake.authenticated)

        val text = "text/plain".toMediaType()
        assertTrue(
            fake.login(
                "spec-user".toRequestBody(text),
                "spec-password".toRequestBody(text),
                "true".toRequestBody(text),
                "/",
            ).isSuccessful
        )
        assertTrue(fake.authenticated)

        val login = fake.calls.single()
        assertEquals("login", login.operation)
        assertEquals("spec-user", login.arguments["username"])
        assertEquals("spec-password", login.arguments["password"])
        assertEquals("true", login.arguments["rememberMe"])
        assertEquals("/", login.arguments["returnUrl"])

        assertTrue(fake.logout().isSuccessful)
        assertFalse(fake.authenticated)
    }

    @Test
    fun fileSystemAndLocalizationPassthrough() = runTest {
        val fake = FakeProwlarrSettings()
        val listing = JsonObject(
            mapOf("parent" to JsonPrimitive("/media"), "directories" to JsonPrimitive(2))
        )
        fake.fileSystemListings["/media"] = listing
        fake.fileSystemTypes["/media/tv"] = JsonPrimitive("folder")
        fake.localizationOptions += LocalizationOption(name = "English", value = "en")
        fake.localizationDictionary = JsonObject(mapOf("Enabled" to JsonPrimitive("Enabled")))

        assertEquals(listing, fake.getFileSystem("/media", true, false))
        assertEquals(JsonObject(emptyMap()), fake.getFileSystem(null, null, null))
        assertEquals(JsonPrimitive("folder"), fake.getFileSystemType("/media/tv"))
        assertEquals(JsonPrimitive("folder"), fake.getFileSystemType("/unknown"))
        val dictionary = fake.getLocalization()
        assertEquals("Enabled", (dictionary["Enabled"] as JsonPrimitive).content)
        assertEquals(1, fake.getLocalizationOptions().size)
    }

    @Test
    fun latencyFailureAndErrorInjection() = runTest {
        val fake = FakeProwlarrSettings()
        fake.downloadClients[1] = DownloadClientResource(id = 1)
        var waited = false
        fake.latency = { waited = true }
        fake.getDownloadClient(1)
        assertTrue(waited)

        fake.failure = { java.io.IOException("transport gone") }
        val transport = runCatching { fake.getDownloadClient(1) }.exceptionOrNull()
        assertTrue(transport is java.io.IOException)

        fake.failure = { null }
        fake.errorStatus = { 503 }
        val plainError = runCatching { fake.getDownloadClient(1) }.exceptionOrNull()
        assertTrue(plainError is HttpException)
        assertEquals(503, (plainError as HttpException).code())
        assertEquals(503, fake.testAllDownloadClients().code())
        val loginResponse = fake.login(
            "u".toRequestBody("text/plain".toMediaType()),
            "p".toRequestBody("text/plain".toMediaType()),
        )
        assertEquals(503, loginResponse.code())
        assertFalse(fake.authenticated)
    }

    @Test
    fun recordsEveryCallWithArguments() = runTest {
        val fake = FakeProwlarrSettings()
        fake.downloadClients[1] = DownloadClientResource(id = 1)
        fake.deleteDownloadClient(1)
        val call = fake.calls.single()
        assertEquals("deleteDownloadClient", call.operation)
        assertEquals(1, call.arguments["id"])
    }

    @Test
    fun responseErrorsCarryBodyLikeRetrofit() = runTest {
        val fake = FakeProwlarrSettings()
        fake.errorStatus = { 401 }
        val response = fake.testDownloadClient(true, DownloadClientResource(id = 1))
        assertEquals(401, response.code())
        assertEquals("{}", response.errorBody()?.string())
    }
}
