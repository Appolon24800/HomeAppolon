package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrFileSystemRepositoryTest {

    private lateinit var fake: FakeLidarrSystem

    @Before
    fun setUp() {
        fake = FakeLidarrSystem()
        fake.fileSystem = buildJsonObject {
            put("parent", "/")
            put("directories", kotlinx.serialization.json.JsonArray(emptyList()))
        }
    }

    private fun TestScope.repository() = LidarrFileSystemRepository(
        fileSystemApi = fake,
        ioContext = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `listing parses directories and parent from the raw json`() = runTest {
        fake.fileSystem = buildJsonObject {
            put("parent", "/")
            put(
                "directories",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        buildJsonObject {
                            put("name", "music")
                            put("path", "/music")
                            put("type", "FOLDER")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("name", "boot")
                            put("path", "/boot")
                            put("type", "FOLDER")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("name", "sda1")
                            put("path", "/media/sda1")
                            put("type", "DRIVE")
                        },
                    )
                },
            )
        }
        val repository = repository()

        val result = repository.list("/")

        val listing = (result as ArrResult.Ok).value
        assertEquals("/", listing.parent)
        // Drives sort first, then folders alphabetically.
        assertEquals(listOf("/media/sda1", "/boot", "/music"), listing.directories.map { it.path })
        assertTrue(listing.directories.first().isDrive)
        assertEquals("/", listing.effectivePath)
    }

    @Test
    fun `a listing without directories renders empty instead of failing`() = runTest {
        fake.fileSystem = buildJsonObject {
            put("parent", "/music")
        }
        val repository = repository()

        val result = repository.list("/music")

        val listing = (result as ArrResult.Ok).value
        assertTrue(listing.directories.isEmpty())
        assertEquals("/music", listing.parent)
        // With no directory rows the breadcrumb falls back to the request.
        assertEquals("/music", listing.effectivePath)
    }

    @Test
    fun `transport failures surface as network errors`() = runTest {
        fake.error = { op -> if (op == "getFileSystem") IOException("offline") else null }
        val repository = repository()

        val result = repository.list("/")

        assertTrue((result as ArrResult.Err).error is ArrError.Network)
    }
}
