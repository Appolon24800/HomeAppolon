package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.SearchResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FakeLidarrDiscoveryTest {
    @Test
    fun importListCrudAppliesBulkEdits() = runTest {
        val fake = FakeLidarrDiscovery()
        val created = fake.addImportList(
            resource = ImportListResource(name = "Example", tags = setOf(1))
        )
        assertEquals(1, created.id)
        assertEquals(created, fake.getImportList(1))

        fake.updateImportList(1, resource = created.copy(name = "Renamed"))
        assertEquals("Renamed", fake.getImportList(1).name)

        fake.importLists[2] = ImportListResource(id = 2, name = "Second", tags = setOf(3))
        val merged = fake.updateImportLists(
            ImportListBulkResource(
                ids = listOf(1, 2), tags = setOf(2), applyTags = ApplyTags.ADD,
                enableAutomaticAdd = true, rootFolderPath = "/music2", qualityProfileId = 3,
            )
        )
        // The spec types the bulk response as one resource; the fake merges in id
        // order and returns the last one, list 2 here.
        assertEquals(2, merged.id)
        assertEquals(setOf(2, 3), merged.tags)
        assertEquals(setOf(1, 2), fake.getImportList(1).tags)
        assertEquals(setOf(2, 3), fake.getImportList(2).tags)
        assertTrue(fake.getImportList(2).enableAutomaticAdd)

        fake.deleteImportLists(ImportListBulkResource(ids = listOf(2)))
        assertNull(fake.importLists[2])

        fake.testImportList(resource = created)
        fake.testAllImportLists()
        fake.importListAction("search", created)
        assertEquals(listOf(created), fake.testedImportLists)
        assertEquals(1, fake.testAllCount)
        assertEquals(listOf(created), fake.importListActions["search"])

        fake.deleteImportList(1)
        assertTrue(fake.importLists.isEmpty())
    }

    @Test
    fun importListExclusionCrud() = runTest {
        val fake = FakeLidarrDiscovery()
        val created = fake.addImportListExclusion(
            ImportListExclusionResource(foreignId = "abc", artistName = "Example Artist")
        )
        assertEquals(1, created.id)
        assertEquals(listOf(created), fake.getImportListExclusions())

        val updated = fake.updateImportListExclusion("1", created.copy(artistName = "Renamed"))
        assertEquals("Renamed", updated.artistName)
        assertEquals("Renamed", fake.getImportListExclusion(1).artistName)

        fake.deleteImportListExclusion(1)
        assertTrue(fake.importListExclusions.isEmpty())
    }

    @Test
    fun customFilterCrudUsesStringUpdateId() = runTest {
        val fake = FakeLidarrDiscovery()
        val created = fake.addCustomFilter(
            CustomFilterResource(type = "artist", label = "Monitored")
        )
        assertEquals(1, created.id)
        assertEquals(listOf(created), fake.getCustomFilters())

        val updated = fake.updateCustomFilter("1", created.copy(label = "Renamed"))
        assertEquals("Renamed", updated.label)

        fake.deleteCustomFilter(1)
        assertTrue(fake.customFilters.isEmpty())
    }

    @Test
    fun unknownIdsReturn404() = runTest {
        val fake = FakeLidarrDiscovery()
        val errors = listOf<suspend () -> Unit>(
            { fake.getImportList(9) },
            { fake.getImportListExclusion(9) },
            { fake.getCustomFilter(9) },
            { fake.getMissingById(9) },
            { fake.getCutoffById(9) },
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
    fun missingPagesFilterAndJoinsArtist() = runTest {
        val fake = FakeLidarrDiscovery()
        fake.artists[2] = ArtistResource(id = 2, artistName = "Example Artist")
        fake.missing[3] = AlbumResource(id = 3, title = "Monitored", artistId = 2, monitored = true)
        fake.missing[4] =
            AlbumResource(id = 4, title = "Unmonitored", artistId = 2, monitored = false)
        fake.missing[5] = AlbumResource(id = 5, title = "Another", artistId = 2, monitored = true)

        val default = fake.getMissing()
        assertEquals(2, default.totalRecords)

        val joined = fake.getMissing(includeArtist = true)
        assertTrue(joined.records!!.any { it.artist?.artistName == "Example Artist" })
        assertTrue(fake.getMissing().records!!.all { it.artist == null })

        val unmonitoredOnly = fake.getMissing(monitored = false)
        assertEquals("Unmonitored", unmonitoredOnly.records!!.single().title)

        val sorted = fake.getMissing(
            sortKey = "title", sortDirection = SortDirection.DESCENDING
        )
        assertEquals(listOf("Monitored", "Another"), sorted.records!!.map { it.title })
        assertEquals("Example Artist", fake.getMissingById(3).artist?.artistName)
    }

    @Test
    fun cutoffPagesReuseWantedLogic() = runTest {
        val fake = FakeLidarrDiscovery()
        fake.cutoff[4] =
            AlbumResource(id = 4, title = "Cutoff Album", artistId = 2, monitored = true)
        val page = fake.getCutoff(page = 1, pageSize = 10)
        assertEquals(1, page.totalRecords)
        assertEquals("Cutoff Album", page.records!!.single().title)
        assertEquals("Cutoff Album", fake.getCutoffById(4).title)
    }

    @Test
    fun searchFiltersByArtistNameOrAlbumTitle() = runTest {
        val fake = FakeLidarrDiscovery()
        fake.searchResults += listOf(
            SearchResource(
                id = 1, artist = ArtistResource(id = 2, artistName = "Example Artist")
            ),
            SearchResource(id = 2, album = AlbumResource(id = 3, title = "Other Album")),
        )
        assertEquals(2, fake.search(null).size)
        assertEquals(listOf(1), fake.search("example").map { it.id })
        assertEquals(listOf(2), fake.search("other album").map { it.id })
        assertTrue(fake.search("no match").isEmpty())
    }

    @Test
    fun staticResourcesServeSeededContent() = runTest {
        val fake = FakeLidarrDiscovery()
        fake.staticResources["favicon.ico"] = "icon"
        assertEquals("<html>login</html>", fake.getLoginPage().body()!!.string())
        assertEquals("<html>lidarr</html>", fake.getRoot().body()!!.string())
        assertEquals("icon", fake.getContent("favicon.ico").body()!!.string())
        assertEquals(404, fake.getContent("missing.css").code())
        assertFalse(fake.getStaticResource("missing.css").isSuccessful)
    }

    @Test
    fun latencyAndErrorInjectionRunPerOperation() = runTest {
        val operations = mutableListOf<String>()
        val fake = FakeLidarrDiscovery(
            latency = { operations += it },
            error = { operation ->
                if (operation == "getCustomFilters") HttpException(
                    Response.error<Unit>(500, "".toResponseBody())
                ) else null
            }
        )
        fake.getImportLists()
        assertEquals(listOf("getImportLists"), operations)

        var caught: HttpException? = null
        try {
            fake.getCustomFilters()
        } catch (error: HttpException) {
            caught = error
        }
        assertEquals(500, caught?.code())
        assertEquals(listOf("getImportLists", "getCustomFilters"), operations)
    }
}
