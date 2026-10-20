package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.CollectionUpdateResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the fake's in-memory semantics so UI tests can rely on them. */
class FakeRadarrDiscoveryTest {
    private fun fake(
        latencyMs: Long = 0,
        errorFor: (String) -> Throwable? = { null },
    ) = FakeRadarrDiscovery(
        latencyMs = latencyMs,
        errorFor = errorFor,
        sleep = { },
        now = { "2026-01-01T00:00:00Z" },
    )

    @Test fun importListCrudFollowsServerSemantics() = runTest {
        val fake = fake()
        val created = fake.createImportList(body = ImportListResource(name = "IMDb List"))
        assertEquals(201, created.code())
        assertEquals(1, created.body()!!.id)
        assertEquals("IMDb List", fake.importList(id = 1).body()!!.name)
        val updated = fake.updateImportList(id = 1, body = ImportListResource(name = "Renamed"))
        assertEquals(202, updated.code())
        assertEquals("Renamed", fake.importLists().single().name)
        fake.removeImportList(id = 1)
        assertTrue(fake.importLists.isEmpty())
        assertEquals(404, fake.importList(id = 1).code())
    }

    @Test fun importListBulkMergesTagsAndSettings() = runTest {
        val fake = fake()
        fake.importLists += ImportListResource(id = 1, tags = setOf(2))
        fake.importLists += ImportListResource(id = 2, tags = emptySet())
        val result = fake.updateImportListBulk(
            ImportListBulkResource(
                ids = listOf(1, 2),
                tags = listOf(5),
                applyTags = ApplyTags.ADD,
                enabled = true,
            ),
        )
        assertEquals(200, result.code())
        assertEquals(setOf(2, 5), fake.importLists[0].tags)
        assertEquals(setOf(5), fake.importLists[1].tags)
        assertEquals(true, fake.importLists[0].enabled)
        fake.removeImportListBulk(ImportListBulkResource(ids = listOf(1)))
        assertEquals(listOf(2), fake.importLists.map { it.id })
    }

    @Test fun providerOperationsAreRecordedNotExecuted() = runTest {
        val fake = fake()
        fake.importLists += ImportListResource(id = 3)
        val resource = ImportListResource(name = "Trakt List")
        assertEquals(204, fake.testImportList(body = resource).code())
        assertEquals(resource, fake.testedImportLists.single())
        assertEquals(200, fake.testAllImportLists().code())
        assertEquals(listOf(3), fake.testAllImportLists().body()!!.map { it.id })
        assertEquals(listOf(true), fake.testAllImportLists().body()!!.map { it.isValid })
        fake.importListAction(name = "search", body = resource)
        assertEquals("search" to resource, fake.listActions.single())
    }

    @Test fun importListConfigRoundTrips() = runTest {
        val fake = fake()
        assertEquals(ImportListConfigResource(), fake.importListConfig())
        val updated = fake.updateImportListConfig(
            id = "1",
            body = ImportListConfigResource(listSyncLevel = "disabled"),
        )
        assertEquals(202, updated.code())
        assertEquals("disabled", fake.importListConfig().listSyncLevel)
        assertEquals(200, fake.importListConfigById(id = 1).code())
        assertEquals(404, fake.importListConfigById(id = 9).code())
    }

    @Test fun exclusionsPageAndBulkRemove() = runTest {
        val fake = fake()
        repeat(3) { index ->
            fake.createExclusion(ImportListExclusionResource(movieTitle = "Movie $index"))
        }
        val firstPage = fake.exclusionsPaged(page = 1, pageSize = 2)
        assertEquals(3, firstPage.totalRecords)
        assertEquals(2, firstPage.records!!.size)
        assertEquals(1, fake.exclusionsPaged(page = 2, pageSize = 2).records!!.size)
        val added = fake.addExclusionsBulk(
            listOf(ImportListExclusionResource(movieTitle = "Bulk One"),
                ImportListExclusionResource(movieTitle = "Bulk Two")),
        )
        assertEquals(listOf(4, 5), added.map { it.id })
        fake.removeExclusionsBulk(ImportListExclusionBulkResource(ids = setOf(1, 2, 3)))
        assertEquals(listOf(4, 5), fake.exclusions().map { it.id })
        assertEquals(404, fake.removeExclusion(id = 1).code())
    }

    @Test fun discoveryFiltersRespectCategoryFlags() = runTest {
        val fake = fake()
        fake.discoveredMovies += ImportListMoviesResource(id = 1, tmdbId = 11)
        fake.discoveredMovies += ImportListMoviesResource(id = 2, tmdbId = 12, isTrending = true)
        fake.discoveredMovies += ImportListMoviesResource(id = 3, tmdbId = 13, isPopular = true)
        fake.discoveredMovies += ImportListMoviesResource(
            id = 4, tmdbId = 14, isRecommendation = true,
        )
        assertEquals(listOf(1, 2, 3, 4),
            fake.importListMovies().map { it.id })
        assertEquals(listOf(1, 3, 4),
            fake.importListMovies(includeTrending = false).map { it.id })
        assertEquals(listOf(1, 2, 4),
            fake.importListMovies(includePopular = false).map { it.id })
        assertEquals(listOf(1, 2, 3),
            fake.importListMovies(includeRecommendations = false).map { it.id })
    }

    @Test fun addMoviesAssignsIdsAndAddedTimestamp() = runTest {
        val fake = fake()
        val added = fake.addMovies(listOf(MovieResource(title = "New"), MovieResource(id = 9)))
        assertEquals(listOf(1, 9), added.map { it.id })
        assertEquals("2026-01-01T00:00:00Z", added[0].added)
        assertEquals(2, fake.movies.size)
    }

    @Test fun wantedEndpointsPageOnlyFilelessMovies() = runTest {
        val fake = fake()
        fake.movies += MovieResource(id = 1, monitored = true, hasFile = false)
        fake.movies += MovieResource(id = 2, monitored = false, hasFile = false)
        fake.movies += MovieResource(id = 3, monitored = true, hasFile = true)
        val missing = fake.missing(monitored = true, pageSize = 1)
        assertEquals(1, missing.totalRecords)
        assertEquals(listOf(1), missing.records!!.map { it.id })
        assertEquals(1, missing.page)
        assertEquals(1, missing.pageSize)
        val cutoff = fake.cutoff(page = 3, pageSize = 1)
        assertEquals(2, cutoff.totalRecords)
        assertEquals(emptyList<MovieResource>(), cutoff.records)
        assertEquals(SortDirection.DEFAULT, cutoff.sortDirection)
    }

    @Test fun collectionsBulkUpdateAppliesOnlySetFields() = runTest {
        val fake = fake()
        fake.collections += CollectionResource(id = 1, tmdbId = 100, monitored = false)
        fake.collections += CollectionResource(id = 2, tmdbId = 200, monitored = false)
        val result = fake.updateCollections(
            CollectionUpdateResource(
                collectionIds = listOf(1),
                monitored = true,
                monitorMovies = true,
            ),
        )
        assertEquals(true, result.first { it.id == 1 }.monitored)
        assertEquals(false, result.first { it.id == 2 }.monitored)
        assertEquals(listOf(true), fake.bulkMonitorMovies)
        val updated = fake.updateCollection(id = "1", body = CollectionResource(title = "Renamed"))
        assertEquals(202, updated.code())
        assertEquals("Renamed", fake.collection(id = 1).body()!!.title)
        assertEquals(404, fake.updateCollection(
            id = "not-a-number", body = CollectionResource()).code())
        // PUT replaces the whole resource, so the replaced collection no longer matches the
        // old tmdbId filter; the untouched one still does.
        assertEquals(listOf(2), fake.collections(tmdbId = 200).map { it.id })
        assertEquals(emptyList<Int>(), fake.collections(tmdbId = 100).map { it.id })
    }

    @Test fun customFilterCrudFollowsServerSemantics() = runTest {
        val fake = fake()
        assertEquals(
            201, fake.createCustomFilter(CustomFilterResource(label = "Unmonitored")).code())
        assertEquals(listOf("Unmonitored"), fake.customFilters().map { it.label })
        val updated = fake.updateCustomFilter(
            id = "1", body = CustomFilterResource(label = "Edited"),
        )
        assertEquals(202, updated.code())
        assertEquals("Edited", fake.customFilter(id = 1).body()!!.label)
        assertEquals(200, fake.removeCustomFilter(id = 1).code())
        assertEquals(404, fake.customFilter(id = 1).code())
        assertEquals(404, fake.removeCustomFilter(id = 1).code())
    }

    @Test fun errorInjectionSkipsStateChanges() = runTest {
        val fake = fake(errorFor = { operation ->
            if (operation == "importLists") IOException("offline") else null
        })
        try {
            fake.importLists()
            throw AssertionError("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        // The API call stays broken but the underlying state is untouched and visible.
        fake.importLists += ImportListResource(id = 7)
        assertEquals(listOf(7), fake.importLists.map { it.id })
    }

    @Test fun latencySleepsBeforeEachCall() = runTest {
        val slept = mutableListOf<Long>()
        val fake = FakeRadarrDiscovery(
            latencyMs = 25,
            sleep = { slept += it },
            now = { "2026-01-01T00:00:00Z" },
        )
        fake.importLists()
        fake.customFilters()
        assertEquals(listOf(25L, 25L), slept)
    }

    @Test fun createdResourcesWithoutIdsGetFreshIds() = runTest {
        val fake = fake()
        fake.exclusions += ImportListExclusionResource(id = 4)
        val created = fake.createExclusion(ImportListExclusionResource(movieTitle = "Next"))
        assertEquals(5, created.body()!!.id)
        assertEquals(0, created.body()!!.movieYear)
    }
}
