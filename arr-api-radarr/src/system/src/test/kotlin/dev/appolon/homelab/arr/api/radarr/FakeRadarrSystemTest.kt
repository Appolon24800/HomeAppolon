package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.BackupResource
import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.radarr.model.LogResource
import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.api.radarr.model.TaskResource
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** FakeRadarrSystem behavior used by UI tests: state, latency, and error injection. */
class FakeRadarrSystemTest {
    private fun text(value: String) = value.toRequestBody("text/plain".toMediaType())

    @Test fun readsSeededState() = runTest {
        val fake = FakeRadarrSystem()
        fake.healthItems += HealthResource(id = 1, source = "DiskSpaceCheck")
        fake.taskItems += TaskResource(id = 2, name = "Housekeeping")
        fake.tagItems += TagResource(id = 3, label = "watchlist")

        assertEquals(1, fake.health().single().id)
        assertEquals(2, fake.task(id = 2).body()!!.id)
        assertEquals("watchlist", fake.tags().single().label)
        assertEquals("Radarr", fake.systemStatus().appName)
        assertEquals("v3", fake.apiInfo().current)
    }

    @Test fun tagCrudMutatesState() = runTest {
        val fake = FakeRadarrSystem()
        val created = fake.createTag(TagResource(label = "4k")).body()!!
        assertEquals(1, created.id)
        assertEquals(202, fake.updateTag(id = "1", body = created.copy(label = "remux")).code())
        assertEquals("remux", fake.tags().single().label)
        assertEquals(200, fake.removeTag(id = 1).code())
        assertTrue(fake.tags().isEmpty())
        assertEquals(404, fake.tag(id = 1).code())
    }

    @Test fun tagDetailsDeriveFromTagItems() = runTest {
        val fake = FakeRadarrSystem()
        fake.tagItems += TagResource(id = 1, label = "watchlist")
        fake.tagUsage = mapOf(1 to TagDetailsResource(id = 1, movieIds = listOf(4, 5)))
        val detail = fake.tagDetail(id = 1).body()!!
        assertEquals("watchlist", detail.label)
        assertEquals(listOf(4, 5), detail.movieIds)
        assertEquals(emptyList<Int>(), detail.indexerIds)
    }

    @Test fun autoTaggingCrudAndSchema() = runTest {
        val fake = FakeRadarrSystem()
        val created = fake.createAutoTagging(AutoTaggingResource(name = "SSD")).body()!!
        assertEquals(1, created.id)
        assertEquals(202, fake.updateAutoTagging("1", created.copy(name = "HDD")).code())
        assertEquals("HDD", fake.autoTaggings().single().name)
        assertEquals(200, fake.removeAutoTagging(1).code())
        assertTrue(fake.autoTaggings().isEmpty())
        assertEquals(emptyList<AutoTaggingResource>(), fake.autoTaggingSchema())
    }

    @Test fun configUpdatesPersist() = runTest {
        val fake = FakeRadarrSystem()
        fake.updateHostConfig("1", HostConfigResource(id = 1, port = 1234))
        assertEquals(1234, fake.hostConfig().port)
        assertEquals(1234, fake.hostConfigById(1).body()!!.port)
        assertEquals(404, fake.hostConfigById(2).code())
    }

    @Test fun backupRestoreAndLifecycleFlags() = runTest {
        val fake = FakeRadarrSystem()
        fake.backups += BackupResource(id = 7)
        assertTrue(fake.restoreBackup(id = 7).body()!!.restartRequired)
        assertTrue(fake.restoreRequested)
        assertTrue(fake.restoreBackupUpload().isSuccessful)
        assertTrue(fake.restart().restarting)
        assertTrue(fake.restartRequested)
        assertTrue(fake.shutdown().shuttingDown)
        assertTrue(fake.shutdownRequested)
        assertEquals(404, fake.restoreBackup(id = 8).code())
    }

    @Test fun loginLogoutFlow() = runTest {
        val fake = FakeRadarrSystem()
        fake.credentials = "user" to "pass"
        assertEquals(401, fake.login(
            returnUrl = "/",
            username = text("user"),
            password = text("wrong"),
        ).code())
        assertFalse(fake.loggedIn)
        assertEquals(200, fake.login(
            returnUrl = "/",
            username = text("user"),
            password = text("pass"),
            rememberMe = text("true"),
        ).code())
        assertTrue(fake.loggedIn)
        val attempt = fake.loginAttempts.last()
        assertEquals("user", attempt.username)
        assertEquals("pass", attempt.password)
        assertEquals("true", attempt.rememberMe)
        assertEquals("/", attempt.returnUrl)
        fake.logout()
        assertFalse(fake.loggedIn)
    }

    @Test fun logPagesSeededItems() = runTest {
        val fake = FakeRadarrSystem()
        repeat(3) { index ->
            fake.logItems += LogResource(id = index, level = if (index == 1) "warn" else "info")
        }
        val page = fake.log(page = 1, pageSize = 2)
        assertEquals(3, page.totalRecords)
        assertEquals(2, page.records!!.size)
        val warnings = fake.log(level = "warn")
        assertEquals(1, warnings.totalRecords)
    }

    @Test fun parseFallsBackToTitleEcho() = runTest {
        val fake = FakeRadarrSystem()
        assertEquals("Movie.Title.2026", fake.parse(title = "Movie.Title.2026").title)
    }

    @Test fun latencySleepsBeforeEveryCall() = runTest {
        var slept = 0L
        val fake = FakeRadarrSystem(latencyMs = 25, sleep = { slept += it })
        fake.systemStatus()
        fake.tags()
        assertEquals(50, slept)
    }

    @Test fun errorInjectionRunsBeforeMutation() = runTest {
        val fake = FakeRadarrSystem(errorFor = { op ->
            if (op == "removeTag") IOException("offline") else null
        })
        fake.tagItems += TagResource(id = 1, label = "kept")
        try {
            fake.removeTag(id = 1)
            fail("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        assertEquals(1, fake.tags().size)
    }
}
