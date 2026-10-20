package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseProfileResource
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * In-memory implementations of every calendar and profile client. Seed collections
 * before UI tests. [latency] can suspend on the test scheduler. [error] runs before
 * reads and mutations. Calendar window filters compare RFC 3339 strings, which is
 * order-preserving for the UTC dates Lidarr emits. The spec models tags as a raw
 * string, so tag filtering is left to callers. Deletes and updates of unknown ids
 * throw the same 404 [HttpException] the server would.
 */
class FakeLidarrCalendar(
    var latency: suspend (operation: String) -> Unit = {},
    var error: (operation: String) -> Throwable? = { null },
    var now: () -> String = { Instant.now().toString() },
) : CalendarApi, CalendarFeedApi, QualityDefinitionApi, QualityProfileApi,
    QualityProfileSchemaApi, MetadataProfileApi, MetadataProfileSchemaApi,
    MetadataProviderConfigApi, ReleaseProfileApi, DelayProfileApi, CustomFormatApi,
    LanguageApi {
    val calendar: MutableMap<Int, AlbumResource> = linkedMapOf()
    val qualityDefinitions: MutableMap<Int, QualityDefinitionResource> = linkedMapOf()
    val qualityProfiles: MutableMap<Int, QualityProfileResource> = linkedMapOf()
    val metadataProfiles: MutableMap<Int, MetadataProfileResource> = linkedMapOf()
    val releaseProfiles: MutableMap<Int, ReleaseProfileResource> = linkedMapOf()
    val delayProfiles: MutableMap<Int, DelayProfileResource> = linkedMapOf()
    val customFormats: MutableMap<Int, CustomFormatResource> = linkedMapOf()
    val languages: MutableMap<Int, LanguageResource> = linkedMapOf()

    /** The spec omits both response schemas, so tests seed the passthrough payloads. */
    var customFormatSchema: JsonElement = JsonArray(emptyList())
    var calendarFeed: String = ""

    var metadataProviderConfig: MetadataProviderConfigResource = MetadataProviderConfigResource()
    var qualityProfileSchema: QualityProfileResource = QualityProfileResource()
    var metadataProfileSchema: MetadataProfileResource = MetadataProfileResource()

    val updatedQualityDefinitions: MutableList<List<QualityDefinitionResource>> = mutableListOf()
    val updatedCustomFormatBulk: MutableList<CustomFormatBulkResource> = mutableListOf()
    val deletedCustomFormatBulk: MutableList<CustomFormatBulkResource> = mutableListOf()
    val reorderedDelayProfiles: MutableList<Pair<Int, Int?>> = mutableListOf()

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missing(): Nothing = throw HttpException(
        Response.error<Unit>(404, "{}".toResponseBody("application/json".toMediaType()))
    )

    private fun nextId(ids: Set<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    override suspend fun getCalendar(
        start: String?,
        end: String?,
        unmonitored: Boolean?,
        includeArtist: Boolean?,
        tags: String?,
    ): List<AlbumResource> {
        before("getCalendar")
        return calendar.values
            .filter { unmonitored == true || it.monitored }
            .filter { start == null || (it.releaseDate ?: "") >= start }
            .filter { end == null || (it.releaseDate ?: "") <= end }
            .sortedBy { it.releaseDate ?: "" }
            .map { it.copy(artist = it.artist.takeIf { includeArtist == true }) }
    }

    override suspend fun getCalendarById(id: Int): AlbumResource {
        before("getCalendarById")
        return calendar[id] ?: missing()
    }

    override suspend fun getCalendarFeed(
        pastDays: Int?,
        futureDays: Int?,
        tags: String?,
        unmonitored: Boolean?,
    ): ResponseBody {
        before("getCalendarFeed")
        return calendarFeed.toResponseBody("text/calendar".toMediaType())
    }

    override suspend fun updateQualityDefinition(
        id: String,
        resource: QualityDefinitionResource,
    ): QualityDefinitionResource {
        before("updateQualityDefinition")
        val key = id.toIntOrNull() ?: resource.id
        if (key !in qualityDefinitions) missing()
        val updated = resource.copy(id = key)
        qualityDefinitions[key] = updated
        return updated
    }

    override suspend fun getQualityDefinitionById(id: Int): QualityDefinitionResource {
        before("getQualityDefinitionById")
        return qualityDefinitions[id] ?: missing()
    }

    override suspend fun getQualityDefinitions(): List<QualityDefinitionResource> {
        before("getQualityDefinitions")
        return qualityDefinitions.values.sortedBy { it.weight }
    }

    override suspend fun updateQualityDefinitions(resource: List<QualityDefinitionResource>) {
        before("updateQualityDefinitions")
        updatedQualityDefinitions += resource
        resource.forEach { qualityDefinitions[it.id] = it }
    }

    override suspend fun createQualityProfile(
        resource: QualityProfileResource,
    ): QualityProfileResource {
        before("createQualityProfile")
        val key = nextId(qualityProfiles.keys)
        val created = resource.copy(
            id = key,
            formatItems = resource.formatItems ?: defaultFormatItems(),
        )
        qualityProfiles[key] = created
        return created
    }

    override suspend fun getQualityProfiles(): List<QualityProfileResource> {
        before("getQualityProfiles")
        return qualityProfiles.values.sortedBy { it.id }
    }

    override suspend fun deleteQualityProfile(id: Int) {
        before("deleteQualityProfile")
        qualityProfiles.remove(id) ?: missing()
    }

    override suspend fun updateQualityProfile(
        id: String,
        resource: QualityProfileResource,
    ): QualityProfileResource {
        before("updateQualityProfile")
        val key = id.toIntOrNull() ?: resource.id
        if (key !in qualityProfiles) missing()
        val updated = resource.copy(id = key)
        qualityProfiles[key] = updated
        return updated
    }

    override suspend fun getQualityProfileById(id: Int): QualityProfileResource {
        before("getQualityProfileById")
        return qualityProfiles[id] ?: missing()
    }

    override suspend fun getQualityProfileSchema(): QualityProfileResource {
        before("getQualityProfileSchema")
        return qualityProfileSchema
    }

    override suspend fun createMetadataProfile(
        resource: MetadataProfileResource,
    ): MetadataProfileResource {
        before("createMetadataProfile")
        val key = nextId(metadataProfiles.keys)
        val created = resource.copy(id = key)
        metadataProfiles[key] = created
        return created
    }

    override suspend fun getMetadataProfiles(): List<MetadataProfileResource> {
        before("getMetadataProfiles")
        return metadataProfiles.values.sortedBy { it.id }
    }

    override suspend fun deleteMetadataProfile(id: Int) {
        before("deleteMetadataProfile")
        metadataProfiles.remove(id) ?: missing()
    }

    override suspend fun updateMetadataProfile(
        id: String,
        resource: MetadataProfileResource,
    ): MetadataProfileResource {
        before("updateMetadataProfile")
        val key = id.toIntOrNull() ?: resource.id
        if (key !in metadataProfiles) missing()
        val updated = resource.copy(id = key)
        metadataProfiles[key] = updated
        return updated
    }

    override suspend fun getMetadataProfileById(id: Int): MetadataProfileResource {
        before("getMetadataProfileById")
        return metadataProfiles[id] ?: missing()
    }

    override suspend fun getMetadataProfileSchema(): MetadataProfileResource {
        before("getMetadataProfileSchema")
        return metadataProfileSchema
    }

    override suspend fun getMetadataProviderConfigById(id: Int): MetadataProviderConfigResource {
        before("getMetadataProviderConfigById")
        if (metadataProviderConfig.id != id) missing()
        return metadataProviderConfig
    }

    override suspend fun updateMetadataProviderConfig(
        id: String,
        resource: MetadataProviderConfigResource,
    ): MetadataProviderConfigResource {
        before("updateMetadataProviderConfig")
        val updated = resource.copy(id = id.toIntOrNull() ?: resource.id)
        metadataProviderConfig = updated
        return updated
    }

    override suspend fun getMetadataProviderConfig(): MetadataProviderConfigResource {
        before("getMetadataProviderConfig")
        return metadataProviderConfig
    }

    override suspend fun getReleaseProfileById(id: Int): ReleaseProfileResource {
        before("getReleaseProfileById")
        return releaseProfiles[id] ?: missing()
    }

    override suspend fun updateReleaseProfile(
        id: String,
        resource: ReleaseProfileResource,
    ): ReleaseProfileResource {
        before("updateReleaseProfile")
        val key = id.toIntOrNull() ?: resource.id
        if (key !in releaseProfiles) missing()
        val updated = resource.copy(id = key)
        releaseProfiles[key] = updated
        return updated
    }

    override suspend fun deleteReleaseProfile(id: Int) {
        before("deleteReleaseProfile")
        releaseProfiles.remove(id) ?: missing()
    }

    override suspend fun getReleaseProfiles(): List<ReleaseProfileResource> {
        before("getReleaseProfiles")
        return releaseProfiles.values.sortedBy { it.id }
    }

    override suspend fun createReleaseProfile(
        resource: ReleaseProfileResource,
    ): ReleaseProfileResource {
        before("createReleaseProfile")
        val key = nextId(releaseProfiles.keys)
        val created = resource.copy(id = key)
        releaseProfiles[key] = created
        return created
    }

    override suspend fun createDelayProfile(resource: DelayProfileResource): DelayProfileResource {
        before("createDelayProfile")
        val key = nextId(delayProfiles.keys)
        val created = resource.copy(id = key, order = delayProfiles.size + 1)
        delayProfiles[key] = created
        return created
    }

    override suspend fun getDelayProfiles(): List<DelayProfileResource> {
        before("getDelayProfiles")
        return delayProfiles.values.sortedBy { it.order }
    }

    override suspend fun deleteDelayProfile(id: Int) {
        before("deleteDelayProfile")
        delayProfiles.remove(id) ?: missing()
    }

    override suspend fun updateDelayProfile(
        id: String,
        resource: DelayProfileResource,
    ): DelayProfileResource {
        before("updateDelayProfile")
        val key = id.toIntOrNull() ?: resource.id
        if (key !in delayProfiles) missing()
        val updated = resource.copy(id = key)
        delayProfiles[key] = updated
        return updated
    }

    override suspend fun getDelayProfileById(id: Int): DelayProfileResource {
        before("getDelayProfileById")
        return delayProfiles[id] ?: missing()
    }

    override suspend fun reorderDelayProfile(id: Int, afterId: Int?) {
        before("reorderDelayProfile")
        val moving = delayProfiles.remove(id) ?: missing()
        reorderedDelayProfiles += id to afterId
        val reordered = LinkedHashMap<Int, DelayProfileResource>()
        // The server treats a missing or zero afterId as "move to the top".
        if (afterId == null || afterId == 0) reordered[id] = moving
        for ((key, value) in delayProfiles) {
            reordered[key] = value
            if (afterId != null && afterId != 0 && key == afterId) reordered[id] = moving
        }
        delayProfiles.clear()
        reordered.entries.forEachIndexed { index, entry ->
            delayProfiles[entry.key] = entry.value.copy(order = index + 1)
        }
    }

    override suspend fun getCustomFormatById(id: Int): CustomFormatResource {
        before("getCustomFormatById")
        return customFormats[id] ?: missing()
    }

    override suspend fun updateCustomFormat(
        id: String,
        resource: CustomFormatResource,
    ): CustomFormatResource {
        before("updateCustomFormat")
        val key = id.toIntOrNull() ?: resource.id
        if (key !in customFormats) missing()
        val updated = resource.copy(id = key)
        customFormats[key] = updated
        return updated
    }

    override suspend fun deleteCustomFormat(id: Int) {
        before("deleteCustomFormat")
        customFormats.remove(id) ?: missing()
    }

    override suspend fun getCustomFormats(): List<CustomFormatResource> {
        before("getCustomFormats")
        return customFormats.values.sortedBy { it.id }
    }

    override suspend fun createCustomFormat(resource: CustomFormatResource): CustomFormatResource {
        before("createCustomFormat")
        val key = nextId(customFormats.keys)
        val created = resource.copy(id = key)
        customFormats[key] = created
        return created
    }

    override suspend fun updateCustomFormatBulk(
        resource: CustomFormatBulkResource,
    ): List<CustomFormatResource> {
        before("updateCustomFormatBulk")
        updatedCustomFormatBulk += resource
        val ids = resource.ids.orEmpty()
        return customFormats.values
            .filter { it.id in ids }
            .map {
                val updated = it.copy(
                    includeCustomFormatWhenRenaming = resource.includeCustomFormatWhenRenaming,
                )
                customFormats[updated.id] = updated
                updated
            }
            .sortedBy { it.id }
    }

    override suspend fun deleteCustomFormatBulk(resource: CustomFormatBulkResource) {
        before("deleteCustomFormatBulk")
        deletedCustomFormatBulk += resource
        resource.ids.orEmpty().forEach { customFormats.remove(it) ?: missing() }
    }

    override suspend fun getCustomFormatSchema(): JsonElement {
        before("getCustomFormatSchema")
        return customFormatSchema
    }

    override suspend fun getLanguageById(id: Int): LanguageResource {
        before("getLanguageById")
        return languages[id] ?: missing()
    }

    override suspend fun getLanguages(): List<LanguageResource> {
        before("getLanguages")
        return languages.values.sortedBy { it.id }
    }

    private fun defaultFormatItems(): List<ProfileFormatItemResource> =
        customFormats.values.map { ProfileFormatItemResource(format = it.id, name = it.name) }
}
