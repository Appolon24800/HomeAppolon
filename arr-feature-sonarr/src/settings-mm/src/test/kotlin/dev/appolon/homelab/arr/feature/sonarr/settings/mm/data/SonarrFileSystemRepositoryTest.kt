package dev.appolon.homelab.arr.feature.sonarr.settings.mm.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonarrFileSystemRepositoryTest {

    private lateinit var fake: FakeSonarrSystem

    @Before
    fun setUp() {
        fake = FakeSonarrSystem()
        fake.fileSystemListing = buildJsonObject {
            put("parent", "/tv")
            put("current", "/tv")
            put(
                "directories",
                buildJsonArray {
                    addJsonObject {
                        put("name", "Shows")
                        put("path", "/tv/Shows")
                        put("type", "folder")
                    }
                    addJsonObject {
                        put("name", "Anime")
                        put("path", "/tv/Anime")
                        put("type", "folder")
                    }
                    addJsonObject {
                        put("name", "Media")
                        put("path", "/media")
                        put("type", "drive")
                    }
                },
            )
        }
    }

    private fun repository() = SonarrFileSystemRepository(
        fileSystemApi = fake.fileSystemApi,
        ioContext = Dispatchers.Unconfined,
    )

    @Test
    fun `listing sorts drives first then folders by name`() = runTest {
        val result = repository().list("/tv")

        val page = (result as ArrResult.Ok).value
        assertEquals("/tv", page.parent)
        assertEquals(listOf("Media", "Anime", "Shows"), page.directories.mapNotNull { it.name })
        assertTrue(page.directories.first().isDrive)
    }

    @Test
    fun `missing directories render an empty page`() = runTest {
        fake.fileSystemListing = buildJsonObject { put("parent", "/") }

        val result = repository().list(null)

        val page = (result as ArrResult.Ok).value
        assertTrue(page.directories.isEmpty())
        assertEquals("/", page.parent)
    }

    @Test
    fun `network failures surface as retryable errors`() = runTest {
        fake.error = { op -> if (op == "FileSystem.list") IOException("offline") else null }

        val result = repository().list("/tv")

        assertTrue((result as ArrResult.Err).error is ArrError.Network)
    }
}
