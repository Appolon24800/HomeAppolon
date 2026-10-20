package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** Drives the fake the way UI tests will: seeded state, CRUD, injections. */
class FakeProwlarrIndexersTest {
    @Test
    fun indexerCrudAndBulkTagMerge() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.indexers[1] = IndexerResource(id = 1, name = "One", tags = listOf(2))
        fake.indexers[2] = IndexerResource(id = 2, name = "Two", tags = listOf(2))

        assertEquals(2, fake.getIndexers().size)
        assertEquals("One", fake.getIndexer(1).name)

        val created = fake.createIndexer(null, IndexerResource(name = "Three"))
        assertTrue(created.id != 0)
        assertEquals(3, fake.indexers.size)

        val updated = fake.updateIndexer("1", null, IndexerResource(id = 1, name = "Renamed"))
        assertEquals("Renamed", updated.name)
        assertEquals("Renamed", fake.indexers.getValue(1).name)

        val bulk = fake.updateIndexers(
            IndexerBulkResource(
                ids = listOf(1, 2),
                tags = listOf(7),
                applyTags = ApplyTags.ADD,
                enable = false,
                priority = 60,
            )
        )
        assertEquals(false, bulk.enable)
        // The rename upsert replaced indexer 1 wholesale, so its old tags are
        // gone before the merge; indexer 2 kept its seed tags.
        assertEquals(listOf(7), fake.indexers.getValue(1).tags)
        assertEquals(listOf(2, 7), fake.indexers.getValue(2).tags)

        assertTrue(fake.deleteIndexer(2).isSuccessful)
        assertEquals(listOf(1, created.id), fake.indexers.keys.sorted())

        val bulkDelete = fake.deleteIndexers(IndexerBulkResource(ids = listOf(1)))
        assertTrue(bulkDelete.isSuccessful)
        assertEquals(listOf(created.id), fake.indexers.keys.sorted())
    }

    @Test
    fun unknownReadsFailWith404() = runTest {
        val fake = FakeProwlarrIndexers()
        val error = runCatching { fake.getIndexer(9) }.exceptionOrNull()
        assertTrue(error is HttpException)
        assertEquals(404, (error as HttpException).code())
        assertEquals(404, fake.getAppProfile(9).code())
    }

    @Test
    fun tagDetailsDeriveFromCurrentState() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.tags[4] = TagResource(id = 4, label = "used")
        fake.indexers[1] = IndexerResource(id = 1, tags = listOf(4))
        fake.indexerProxies[2] = IndexerProxyResource(id = 2, tags = listOf(4))
        fake.applications[3] = ApplicationResource(id = 3, tags = listOf(4))
        fake.tagNotificationIds[4] = listOf(5)

        val detail = fake.getTagDetail(4)
        assertEquals("used", detail.label)
        assertEquals(listOf(5), detail.notificationIds)
        assertEquals(listOf(1), detail.indexerIds)
        assertEquals(listOf(2), detail.indexerProxyIds)
        assertEquals(listOf(3), detail.applicationIds)
        assertEquals(1, fake.getTagDetails().size)
    }

    @Test
    fun applicationBulkAppliesSyncLevelAndReplaceTags() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.applications[1] = ApplicationResource(id = 1, tags = listOf(2))
        val updated = fake.updateApplications(
            ApplicationBulkResource(
                ids = listOf(1),
                tags = listOf(6),
                applyTags = ApplyTags.REPLACE,
                syncLevel = ApplicationSyncLevel.ADD_ONLY,
            )
        )
        assertEquals(ApplicationSyncLevel.ADD_ONLY, updated.syncLevel)
        assertEquals(listOf(6), fake.applications.getValue(1).tags)
    }

    @Test
    fun profilesSchemaAndCreate() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.appProfileSchema = AppProfileResource(name = "Template", enableRss = true)
        assertEquals("Template", fake.getAppProfileSchema().name)
        val created = fake.createAppProfile(AppProfileResource(name = "Mine"))
        assertEquals("Mine", created.name)
        assertTrue(fake.getAppProfiles().any { it.name == "Mine" })
    }

    @Test
    fun latencyFailureAndErrorInjection() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.indexers[1] = IndexerResource(id = 1)
        var waited = false
        fake.latency = { waited = true }
        fake.getIndexer(1)
        assertTrue(waited)

        fake.failure = { java.io.IOException("transport gone") }
        val transport = runCatching { fake.getIndexer(1) }.exceptionOrNull()
        assertTrue(transport is java.io.IOException)

        fake.failure = { null }
        fake.errorStatus = { 503 }
        val plainError = runCatching { fake.getIndexer(1) }.exceptionOrNull()
        assertTrue(plainError is HttpException)
        assertEquals(503, (plainError as HttpException).code())
        assertEquals(503, fake.testAllIndexers().code())
    }

    @Test
    fun recordsEveryCallWithArguments() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.indexers[1] = IndexerResource(id = 1)
        fake.deleteIndexer(1)
        val call = fake.calls.single()
        assertEquals("deleteIndexer", call.operation)
        assertEquals(1, call.arguments["id"])
    }

    @Test
    fun responseErrorsCarryBodyLikeRetrofit() = runTest {
        val fake = FakeProwlarrIndexers()
        fake.errorStatus = { 401 }
        val response = fake.testIndexer(true, IndexerResource(id = 1))
        assertEquals(401, response.code())
        assertEquals("{}", response.errorBody()?.string())
    }
}
