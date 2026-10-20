package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemModel
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.core.ArrResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrFileSystemRepositoryTest {

    private lateinit var fake: FakeRadarrSystem

    @Before
    fun setUp() {
        fake = FakeRadarrSystem()
        // The committed fake returns entries whose own path equals the
        // requested one, so the fixtures below model the children of
        // /mnt under that contract.
        fake.fileSystemResult = FileSystemResult(
            parent = "/",
            directories = listOf(
                FileSystemModel(type = FileSystemEntityType.FOLDER, name = "movies", path = "/mnt"),
                FileSystemModel(type = FileSystemEntityType.DRIVE, name = "media", path = "/mnt"),
                FileSystemModel(type = FileSystemEntityType.FOLDER, name = "films", path = "/mnt"),
            ),
            files = listOf(
                FileSystemModel(type = FileSystemEntityType.FILE, name = "notes.txt", path = "/mnt"),
            ),
        )
    }

    private fun TestScope.repository() = RadarrFileSystemRepository(
        fileSystemApi = fake,
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun <T> ok(result: ArrResult<T>): T = (result as ArrResult.Ok).value

    @Test
    fun `lists directories drives-first then alphabetical`() = runTest {
        val repository = repository()

        val listing = repository.list("/mnt")

        val names = ok(listing).directories.mapNotNull { it.name }
        assertEquals(listOf("media", "films", "movies"), names)
        // Files never leak into the picker even when the server lists them.
        assertEquals(3, ok(listing).directories.size)
    }

    @Test
    fun `the listing keeps the server-reported parent for the up button`() = runTest {
        val repository = repository()

        val listing = repository.list("/mnt")

        assertEquals("/", ok(listing).parent)
    }

    @Test
    fun `network failures surface as errors, never throws`() = runTest {
        fake.errorFor = { op -> if (op == "fileSystem") IOException("offline") else null }
        val repository = repository()

        val listing = repository.list("/mnt")

        assertTrue(listing is ArrResult.Err)
    }
}
