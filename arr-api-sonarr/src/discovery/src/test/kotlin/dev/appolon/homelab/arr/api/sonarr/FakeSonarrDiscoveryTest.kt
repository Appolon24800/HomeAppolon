package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.ListSyncLevelType
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeSonarrDiscoveryTest {
    @Test
    fun importListLifecycle() = runTest {
        val fake = FakeSonarrDiscovery()
        fake.importListStore[1] = ImportListResource(id = 1, name = "Trakt", qualityProfileId = 1)
        fake.importListStore[2] = ImportListResource(id = 2, name = "Plex")
        assertEquals(2, fake.importList.list().size)
        val created = fake.importList.create(
            forceSave = true, body = ImportListResource(name = "Simkl")
        )
        assertEquals(3, created.id)
        assertEquals(1, fake.forcedSaves)
        val updated = fake.importList.update(
            id = 1, forceSave = null, body = ImportListResource(name = "Trakt 2")
        )
        assertEquals("Trakt 2", fake.importListStore[1]!!.name)
        assertEquals(1, updated.id)
        assertEquals(200, fake.importList.get(1).code())
        assertEquals(404, fake.importList.get(99).code())
        val bulk = fake.importList.updateBulk(
            ImportListBulkResource(
                ids = listOf(1, 2), enableAutomaticAdd = true,
                tags = listOf(5), applyTags = ApplyTags.ADD, qualityProfileId = 7
            )
        )
        assertTrue(bulk.enableAutomaticAdd)
        assertEquals(7, fake.importListStore[2]!!.qualityProfileId)
        fake.importList.deleteBulk(ImportListBulkResource(ids = listOf(2)))
        assertEquals(setOf(1, 3), fake.importListStore.keys)
        fake.importListSchemas.add(ImportListResource(implementationName = "Excluded"))
        assertEquals(1, fake.importList.schema().size)
        fake.importList.test(forceTest = true, body = ImportListResource(name = "Simkl"))
        assertEquals(1, fake.forcedTests)
        assertEquals(listOf("Simkl"), fake.testedLists.map { it.name })
        fake.importList.testAll()
        assertEquals(1, fake.testedAllCount.size)
        fake.importList.action("discover", ImportListResource(name = "Simkl"))
        assertEquals(1, fake.actionCalls["discover"]!!.size)
        fake.importList.delete(1)
        assertTrue(1 !in fake.importListStore)
    }

    @Test
    fun configAndExclusions() = runTest {
        val fake = FakeSonarrDiscovery()
        fake.importListConfig = ImportListConfigResource(
            id = 1, listSyncLevel = ListSyncLevelType.LOG_ONLY, listSyncTag = 3
        )
        assertEquals(ListSyncLevelType.LOG_ONLY, fake.importListConfigApi.list().listSyncLevel)
        assertEquals(200, fake.importListConfigApi.get(1).code())
        assertEquals(404, fake.importListConfigApi.get(9).code())
        val saved = fake.importListConfigApi.update(
            "1", ImportListConfigResource(listSyncLevel = ListSyncLevelType.KEEP_AND_TAG)
        )
        assertEquals(ListSyncLevelType.KEEP_AND_TAG, fake.importListConfig.listSyncLevel)
        assertEquals(1, saved.id)
        fake.importListExclusionStore[1] = ImportListExclusionResource(id = 1, tvdbId = 11, title = "A")
        fake.importListExclusionStore[2] = ImportListExclusionResource(id = 2, tvdbId = 22, title = "B")
        assertEquals(2, fake.importListExclusion.list().size)
        val page = fake.importListExclusion.paged(
            page = 1, pageSize = 1, sortKey = "title", sortDirection = SortDirection.ASCENDING
        )
        assertEquals(2, page.totalRecords)
        assertEquals(listOf("A"), page.records!!.map { it.title })
        fake.importListExclusion.delete(1)
        fake.importListExclusion.deleteBulk(ImportListExclusionBulkResource(ids = listOf(2)))
        assertTrue(fake.importListExclusionStore.isEmpty())
        val created = fake.importListExclusion.create(
            ImportListExclusionResource(tvdbId = 33, title = "C")
        )
        assertEquals(1, created.id)
    }

    @Test
    fun customFiltersAndWantedPages() = runTest {
        val fake = FakeSonarrDiscovery()
        fake.customFilterStore[1] = CustomFilterResource(id = 1, type = "seriesIndex", label = "Monitored")
        val created = fake.customFilter.create(
            CustomFilterResource(type = "seriesIndex", label = "Missing")
        )
        assertEquals(2, created.id)
        fake.customFilter.update("1", CustomFilterResource(type = "seriesIndex", label = "Renamed"))
        assertEquals("Renamed", fake.customFilterStore[1]!!.label)
        assertEquals(404, fake.customFilter.get(9).code())
        fake.customFilter.delete(2)
        assertEquals(setOf(1), fake.customFilterStore.keys)
        fake.missingEpisodes.addAll(
            listOf(
                EpisodeResource(id = 1, monitored = true),
                EpisodeResource(id = 2, monitored = true),
                EpisodeResource(id = 3, monitored = false)
            )
        )
        val missing = fake.missingApi.list(
            page = 1, pageSize = 1, sortKey = null, sortDirection = null,
            includeSeries = false, includeImages = false, monitored = null
        )
        assertEquals(2, missing.totalRecords)
        assertEquals(listOf(1), missing.records!!.map { it.id })
        assertEquals(200, fake.missingApi.get(1).code())
        assertEquals(200, fake.missingApi.get(3).code())
        assertEquals(404, fake.missingApi.get(99).code())
        val unmonitored = fake.missingApi.list(monitored = false)
        assertEquals(listOf(3), unmonitored.records!!.map { it.id })
        fake.cutoffEpisodes.add(EpisodeResource(id = 7, monitored = true))
        val cutoff = fake.cutoff.list(
            page = null, pageSize = null, sortKey = null, sortDirection = null,
            includeSeries = null, includeEpisodeFile = null, includeImages = null, monitored = null
        )
        assertEquals(1, cutoff.totalRecords)
        assertEquals(200, fake.cutoff.get(7).code())
    }

    @Test
    fun latencyRunsBeforeErrorInjection() = runTest {
        val operations = mutableListOf<String>()
        val fake = FakeSonarrDiscovery(
            latency = { operations.add(it) },
            error = { if (it == "CustomFilter.list") IOException("offline") else null }
        )
        try {
            fake.customFilter.list()
            fail("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
        assertEquals(0, fake.importList.list().size)
        assertEquals(listOf("CustomFilter.list", "ImportList.list"), operations)
    }
}
