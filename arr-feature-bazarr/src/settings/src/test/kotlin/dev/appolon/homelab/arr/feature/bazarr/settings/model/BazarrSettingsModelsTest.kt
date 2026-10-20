package dev.appolon.homelab.arr.feature.bazarr.settings.model

import dev.appolon.homelab.arr.feature.bazarr.settings.languageRow
import dev.appolon.homelab.arr.feature.bazarr.settings.logRow
import dev.appolon.homelab.arr.feature.bazarr.settings.profileItem
import dev.appolon.homelab.arr.feature.bazarr.settings.profileRow
import dev.appolon.homelab.arr.feature.bazarr.settings.providerRow
import dev.appolon.homelab.arr.feature.bazarr.settings.settingsDataPayload
import dev.appolon.homelab.arr.feature.bazarr.settings.taskRow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Defensive parsing of the schema-less settings payloads. */
class BazarrSettingsModelsTest {

    @Test
    fun `languages parse enabled flags and drop rows without a code`() {
        val payload = settingsDataPayload(
            listOf(
                languageRow("en", "English", enabled = true, hi = true),
                languageRow("de", "German"),
                buildJsonObject { put("name", "Broken") },
            ),
        )

        val rows = payload.toLanguageSettings()

        assertEquals(2, rows.size)
        val english = rows.first { it.code2 == "en" }
        assertTrue(english.enabled)
        assertTrue(english.hearingImpaired)
        assertEquals("English (en)", english.label)
        assertEquals("de", rows.first { it.code2 == "de" }.code2)
        assertTrue(rows.none { it.code2.isBlank() })
    }

    @Test
    fun `languages tolerate a bare root array`() {
        val payload = buildJsonObject {
            put("ignored", JsonArray(listOf(languageRow("fr", "French", enabled = true))))
        }

        val rows = payload.toLanguageSettings()

        assertEquals(1, rows.size)
        assertEquals("fr", rows.single().code2)
    }

    @Test
    fun `profiles parse cutoff slots and release rules`() {
        val payload = settingsDataPayload(
            listOf(
                profileRow(
                    profileId = 1,
                    name = "English",
                    cutoff = "en",
                    items = listOf(profileItem("en", hi = true), profileItem("de", forced = true)),
                    mustContain = listOf("amzn", "web"),
                    mustNotContain = listOf("cam"),
                    originalFormat = listOf("en"),
                ),
                buildJsonObject { put("name", "No id") },
            ),
        )

        val rows = payload.toLanguageProfiles()

        assertEquals(1, rows.size)
        val profile = rows.single()
        assertEquals("en", profile.cutoff)
        assertEquals(2, profile.items.size)
        assertTrue(profile.items.first().hearingImpaired)
        assertTrue(profile.items.last().forced)
        assertEquals(listOf("amzn", "web"), profile.mustContain)
        assertEquals(listOf("cam"), profile.mustNotContain)
        assertEquals(listOf("en"), profile.originalFormat)
    }

    @Test
    fun `providers keep dynamic fields and drop reserved keys`() {
        val payload = settingsDataPayload(
            listOf(
                providerRow(
                    provider = "opensubtitles",
                    status = "Good",
                    retry = "0",
                    extra = mapOf("username" to "someone", "tier" to "premium"),
                ),
            ),
        )

        val rows = payload.toProviderStatuses()

        val provider = rows.single()
        assertEquals("Good", provider.status)
        assertEquals(
            listOf<Pair<String, String>>("username" to "someone", "tier" to "premium"),
            provider.extraFields,
        )
    }

    @Test
    fun `tasks parse iso next run and interval labels`() {
        val payload = settingsDataPayload(
            listOf(
                taskRow(jobId = "update_series", name = "Update Series", interval = "1h"),
                taskRow(jobId = "dead", name = "Dead", nextRun = ""),
            ),
        )

        val rows = payload.toScheduledTasks()

        val update = rows.first { it.jobId == "update_series" }
        assertEquals("1h", update.intervalLabel)
        assertTrue(update.nextRunMillis > 0)
        assertEquals(0L, rows.first { it.jobId == "dead" }.nextRunMillis)
    }

    @Test
    fun `jobs require an id and default the rest`() {
        val payload = settingsDataPayload(
            listOf(
                buildJsonObject { put("id", 7) },
                buildJsonObject { put("queueName", "orphan") },
            ),
        )

        val rows = payload.toJobRows()

        val job = rows.single()
        assertEquals(7, job.id)
        assertEquals("", job.queueName)
        assertEquals("", job.status)
    }

    @Test
    fun `status rows flatten a data wrapper and compact nested members`() {
        val payload = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put("version", "1.6.2")
                    put("uses_radarr", "True")
                    put(
                        "folder",
                        buildJsonObject {
                            put("movies", "/movies")
                            put("series", "/tv")
                        },
                    )
                },
            )
        }

        val rows = payload.toStatusRows()

        assertEquals("1.6.2", rows.first { it.key == "version" }.value)
        assertEquals("True", rows.first { it.key == "uses_radarr" }.value)
        val folder = rows.first { it.key == "folder" }.value
        assertTrue(folder.contains("/movies") && folder.contains("/tv"))
    }

    @Test
    fun `status rows split general interest keys from the rest`() {
        val rows = payloadOf(
            "uses_radarr" to "True",
            "anti_captcha_key" to "set",
            "version" to "1.6.2",
        ).toStatusRows()

        val general = rows.generalStatusRows()
        val other = rows.otherStatusRows()

        assertEquals(setOf("uses_radarr", "anti_captcha_key"), general.map { it.key }.toSet())
        assertEquals(setOf("version"), other.map { it.key }.toSet())
    }

    @Test
    fun `backups drop rows without a filename`() {
        val payload = settingsDataPayload(
            listOf(
                backupRowWithoutImport(filename = "bazarr_backup.zip"),
                buildJsonObject { put("size", "1 KB") },
            ),
        )

        val rows = payload.toBackupFiles()

        assertEquals(listOf("bazarr_backup.zip"), rows.map { it.filename })
    }

    @Test
    fun `log entries drop blank messages`() {
        val payload = settingsDataPayload(
            listOf(logRow(message = "Imported subtitles"), logRow(message = "")),
        )

        val rows = payload.toLogEntries()

        assertEquals(listOf("Imported subtitles"), rows.map { it.message })
    }

    private fun payloadOf(vararg pairs: Pair<String, String>) = buildJsonObject {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    private fun backupRowWithoutImport(filename: String) = buildJsonObject {
        put("filename", filename)
        put("type", "backup")
    }
}
