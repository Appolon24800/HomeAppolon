package dev.appolon.homelab.arr.feature.radarr.search

import dev.appolon.homelab.arr.api.radarr.ManualImportApi
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseApi
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleasePushApi
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseResource

/**
 * In-memory fake for the feature-owned Release and ReleasePush interfaces,
 * matching the recording style of the api module's `FakeRadarrQueue`
 * (which cannot cover these endpoints: they are not part of that module in
 * this checkout). [errorFor] receives the operation name and throws before
 * any state mutation.
 */
class FakeRadarrReleases(
    var errorFor: (String) -> Throwable? = { null },
) : RadarrReleaseApi, RadarrReleasePushApi {

    val releases = mutableListOf<RadarrReleaseResource>()
    val grabs = mutableListOf<RadarrReleaseResource>()
    val pushes = mutableListOf<RadarrReleaseResource>()
    val searchedMovieIds = mutableListOf<Int>()
    var listCalls = 0

    override suspend fun list(movieId: Int): List<RadarrReleaseResource> {
        errorFor("list")?.let { throw it }
        listCalls += 1
        searchedMovieIds += movieId
        return releases.filter { it.movieId == null || it.movieId == movieId }
    }

    override suspend fun grab(body: RadarrReleaseResource): RadarrReleaseResource {
        errorFor("grab")?.let { throw it }
        grabs += body
        return body
    }

    override suspend fun push(body: RadarrReleaseResource): List<RadarrReleaseResource> {
        errorFor("push")?.let { throw it }
        pushes += body
        return listOf(body)
    }
}

/**
 * Delegating wrapper around the api module's queue fake that records every
 * reprocess payload, so import tests can assert the exact wire body.
 */
class RecordingManualImport(private val fake: dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue) :
    ManualImportApi by fake {

    val posted = mutableListOf<List<ManualImportReprocessResource>>()

    override suspend fun reprocessManualImport(
        body: List<ManualImportReprocessResource>,
    ): retrofit2.Response<List<ManualImportReprocessResource>> {
        posted += body
        return fake.reprocessManualImport(body)
    }
}
