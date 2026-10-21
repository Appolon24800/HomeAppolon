package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CalendarReleaseType
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Mutable single-test state implementing every calendar and quality group
 * interface. Inject a suspending [sleep] such as coroutine delay to use virtual
 * time without a runtime coroutine dependency. The default sleep blocks the
 * calling thread, so UI tests should inject it. This fake never contacts a
 * server. [errorFor] receives the operation name and runs before any state
 * mutation, so an injected failure leaves the fake unchanged.
 *
 * Calendar window filtering compares ISO-8601 strings lexicographically against
 * the earliest known release date of each movie, which is exact for the fixed
 * `yyyy-MM-ddTHH:mm:ss` forms Radarr emits. The `tags` filter follows the wire
 * format: comma-separated tag ids matching any movie tag.
 */
class FakeRadarrCalendar(
    var latencyMs: Long = 0,
    var errorFor: (String) -> Throwable? = { null },
    private val sleep: suspend (Long) -> Unit = { Thread.sleep(it) },
) : CalendarApi, CalendarFeedApi, LanguageApi, QualityDefinitionApi,
    QualityProfileApi, QualityProfileSchemaApi, ReleaseProfileApi,
    DelayProfileApi, CustomFormatApi {
    val movies = mutableListOf<MovieResource>()
    val languages = mutableListOf<LanguageResource>()
    val qualityDefinitions = mutableListOf<QualityDefinitionResource>()
    val qualityProfiles = mutableListOf<QualityProfileResource>()
    val releaseProfiles = mutableListOf<ReleaseProfileResource>()
    val delayProfiles = mutableListOf<DelayProfileResource>()
    val customFormats = mutableListOf<CustomFormatResource>()

    /** Served by the limits endpoint; tests set their own bounds. */
    var qualityDefinitionLimits: QualityDefinitionLimitsResource =
        QualityDefinitionLimitsResource(min = 0, max = 1000)

    /** Served by the custom format schema endpoint as the preset list. */
    var customFormatPresets: List<CustomFormatSpecificationSchema> = emptyList()

    /** Feed calls are recorded because the endpoint returns no body. */
    val feedRequests = mutableListOf<FeedRequest>()

    data class FeedRequest(
        val pastDays: Int?,
        val futureDays: Int?,
        val tags: String?,
        val unmonitored: Boolean?,
        val releaseTypes: List<CalendarReleaseType>?,
    )

    private suspend fun before(operation: String) {
        require(latencyMs >= 0) { "Latency must be nonnegative" }
        if (latencyMs > 0) sleep(latencyMs)
        errorFor(operation)?.let { throw it }
    }

    private fun <T> notFound(): Response<T> = Response.error(404, "{}".toResponseBody())

    private fun nextId(ids: List<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    private fun movieDate(movie: MovieResource): String? = listOf(
        movie.inCinemas,
        movie.digitalRelease,
        movie.physicalRelease,
        movie.releaseDate,
    ).filterNotNull().minOrNull()

    override suspend fun calendar(
        start: String?,
        end: String?,
        unmonitored: Boolean?,
        tags: String?,
    ): List<MovieResource> {
        before("calendar")
        val requestedTags = tags?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        return movies.filter { movie ->
            val date = movieDate(movie)
            (start == null || date == null || date >= start) &&
                (end == null || date == null || date <= end) &&
                (unmonitored == true || movie.monitored) &&
                (requestedTags.isEmpty() ||
                    movie.tags.orEmpty().any { it in requestedTags })
        }
    }

    override suspend fun calendarFeed(
        pastDays: Int?,
        futureDays: Int?,
        tags: String?,
        unmonitored: Boolean?,
        releaseTypes: List<CalendarReleaseType>?,
    ): Response<Unit> {
        before("calendarFeed")
        feedRequests += FeedRequest(pastDays, futureDays, tags, unmonitored, releaseTypes)
        return Response.success(Unit)
    }

    override suspend fun languages(): List<LanguageResource> {
        before("languages")
        return languages.toList()
    }

    override suspend fun language(id: Int): Response<LanguageResource> {
        before("language")
        val found = languages.singleOrNull { it.id == id }
        return if (found == null) notFound() else Response.success(found)
    }

    override suspend fun qualityDefinitions(): List<QualityDefinitionResource> {
        before("qualityDefinitions")
        return qualityDefinitions.toList()
    }

    override suspend fun qualityDefinition(id: Int): Response<QualityDefinitionResource> {
        before("qualityDefinition")
        val found = qualityDefinitions.singleOrNull { it.id == id }
        return if (found == null) notFound() else Response.success(found)
    }

    override suspend fun updateQualityDefinition(
        id: String,
        body: QualityDefinitionResource,
    ): Response<QualityDefinitionResource> {
        before("updateQualityDefinition")
        val index = id.toIntOrNull()?.let { parsed ->
            qualityDefinitions.indexOfFirst { it.id == parsed }
        } ?: -1
        if (index < 0) return notFound()
        val updated = body.copy(id = qualityDefinitions[index].id)
        qualityDefinitions[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun updateQualityDefinitions(
        body: List<QualityDefinitionResource>,
    ): List<QualityDefinitionResource> {
        before("updateQualityDefinitions")
        qualityDefinitions.clear()
        qualityDefinitions += body
        return qualityDefinitions.toList()
    }

    override suspend fun qualityDefinitionLimits(): QualityDefinitionLimitsResource {
        before("qualityDefinitionLimits")
        return qualityDefinitionLimits
    }

    override suspend fun qualityProfiles(): List<QualityProfileResource> {
        before("qualityProfiles")
        return qualityProfiles.toList()
    }

    override suspend fun createQualityProfile(
        body: QualityProfileResource,
    ): Response<QualityProfileResource> {
        before("createQualityProfile")
        val created = body.copy(id = nextId(qualityProfiles.map { it.id }))
        qualityProfiles += created
        return Response.success(201, created)
    }

    override suspend fun qualityProfile(id: Int): Response<QualityProfileResource> {
        before("qualityProfile")
        val found = qualityProfiles.singleOrNull { it.id == id }
        return if (found == null) notFound() else Response.success(found)
    }

    override suspend fun updateQualityProfile(
        id: String,
        body: QualityProfileResource,
    ): Response<QualityProfileResource> {
        before("updateQualityProfile")
        val index = id.toIntOrNull()?.let { parsed ->
            qualityProfiles.indexOfFirst { it.id == parsed }
        } ?: -1
        if (index < 0) return notFound()
        val updated = body.copy(id = qualityProfiles[index].id)
        qualityProfiles[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeQualityProfile(id: Int): Response<Unit> {
        before("removeQualityProfile")
        qualityProfiles.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun qualityProfileSchema(): QualityProfileResource {
        before("qualityProfileSchema")
        return QualityProfileResource(
            name = "New Profile",
            upgradeAllowed = true,
            items = qualityDefinitions.map { definition ->
                QualityProfileQualityItemResource(
                    id = definition.id,
                    name = definition.title,
                    quality = definition.quality,
                    allowed = false,
                )
            },
            formatItems = customFormats.map { format ->
                ProfileFormatItemResource(format = format.id, name = format.name)
            },
        )
    }

    override suspend fun releaseProfiles(): List<ReleaseProfileResource> {
        before("releaseProfiles")
        return releaseProfiles.toList()
    }

    override suspend fun createReleaseProfile(
        body: ReleaseProfileResource,
    ): Response<ReleaseProfileResource> {
        before("createReleaseProfile")
        val created = body.copy(id = nextId(releaseProfiles.map { it.id }))
        releaseProfiles += created
        return Response.success(201, created)
    }

    override suspend fun releaseProfile(id: Int): Response<ReleaseProfileResource> {
        before("releaseProfile")
        val found = releaseProfiles.singleOrNull { it.id == id }
        return if (found == null) notFound() else Response.success(found)
    }

    override suspend fun updateReleaseProfile(
        id: String,
        body: ReleaseProfileResource,
    ): Response<ReleaseProfileResource> {
        before("updateReleaseProfile")
        val index = id.toIntOrNull()?.let { parsed ->
            releaseProfiles.indexOfFirst { it.id == parsed }
        } ?: -1
        if (index < 0) return notFound()
        val updated = body.copy(id = releaseProfiles[index].id)
        releaseProfiles[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeReleaseProfile(id: Int): Response<Unit> {
        before("removeReleaseProfile")
        releaseProfiles.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun delayProfiles(): List<DelayProfileResource> {
        before("delayProfiles")
        return delayProfiles.toList()
    }

    override suspend fun createDelayProfile(
        body: DelayProfileResource,
    ): Response<DelayProfileResource> {
        before("createDelayProfile")
        val created = body.copy(
            id = nextId(delayProfiles.map { it.id }),
            order = delayProfiles.maxOfOrNull { it.order } ?: 0,
        )
        delayProfiles += created
        return Response.success(201, created)
    }

    override suspend fun delayProfile(id: Int): Response<DelayProfileResource> {
        before("delayProfile")
        val found = delayProfiles.singleOrNull { it.id == id }
        return if (found == null) notFound() else Response.success(found)
    }

    override suspend fun updateDelayProfile(
        id: String,
        body: DelayProfileResource,
    ): Response<DelayProfileResource> {
        before("updateDelayProfile")
        val index = id.toIntOrNull()?.let { parsed ->
            delayProfiles.indexOfFirst { it.id == parsed }
        } ?: -1
        if (index < 0) return notFound()
        val updated = body.copy(id = delayProfiles[index].id, order = delayProfiles[index].order)
        delayProfiles[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeDelayProfile(id: Int): Response<Unit> {
        before("removeDelayProfile")
        delayProfiles.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun reorderDelayProfile(
        id: Int,
        after: Int?,
    ): List<DelayProfileResource> {
        before("reorderDelayProfile")
        val moving = delayProfiles.singleOrNull { it.id == id } ?: delayProfiles.first()
        val rest = delayProfiles.filterNot { it.id == moving.id }
        val insertAt = after?.let { anchor -> rest.indexOfFirst { it.id == anchor } + 1 } ?: 0
        delayProfiles.clear()
        delayProfiles += rest.subList(0, insertAt) + moving + rest.subList(insertAt, rest.size)
        delayProfiles.indices.forEach { index ->
            delayProfiles[index] = delayProfiles[index].copy(order = index)
        }
        return delayProfiles.toList()
    }

    override suspend fun customFormats(): List<CustomFormatResource> {
        before("customFormats")
        return customFormats.toList()
    }

    override suspend fun createCustomFormat(
        body: CustomFormatResource,
    ): Response<CustomFormatResource> {
        before("createCustomFormat")
        val created = body.copy(id = nextId(customFormats.map { it.id }))
        customFormats += created
        return Response.success(201, created)
    }

    override suspend fun customFormat(id: Int): Response<CustomFormatResource> {
        before("customFormat")
        val found = customFormats.singleOrNull { it.id == id }
        return if (found == null) notFound() else Response.success(found)
    }

    override suspend fun updateCustomFormat(
        id: String,
        body: CustomFormatResource,
    ): Response<CustomFormatResource> {
        before("updateCustomFormat")
        val index = id.toIntOrNull()?.let { parsed ->
            customFormats.indexOfFirst { it.id == parsed }
        } ?: -1
        if (index < 0) return notFound()
        val updated = body.copy(id = customFormats[index].id)
        customFormats[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeCustomFormat(id: Int): Response<Unit> {
        before("removeCustomFormat")
        customFormats.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun updateCustomFormats(
        body: CustomFormatBulkResource,
    ): Response<List<CustomFormatResource>> {
        before("updateCustomFormats")
        val ids = body.ids.orEmpty()
        customFormats.indices.forEach { index ->
            val format = customFormats[index]
            if (format.id in ids) {
                customFormats[index] = format.copy(
                    includeCustomFormatWhenRenaming =
                    body.includeCustomFormatWhenRenaming ?: format.includeCustomFormatWhenRenaming,
                )
            }
        }
        return Response.success(customFormats.filter { it.id in ids })
    }

    override suspend fun removeCustomFormats(body: CustomFormatBulkResource): JsonObject {
        before("removeCustomFormats")
        customFormats.removeAll { it.id in body.ids.orEmpty() }
        return JsonObject(emptyMap())
    }

    override suspend fun customFormatSchema(): List<CustomFormatSpecificationSchema> {
        before("customFormatSchema")
        return customFormatPresets.toList()
    }
}
