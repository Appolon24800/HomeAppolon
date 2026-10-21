package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileItemResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseProfileResource
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Mutable single-test state for every calendar-group interface.
 * [latency] runs before [error], which can fail any operation before it
 * mutates state. Callbacks receive `Tag.method`. Calendar windows filter on
 * [EpisodeResource.airDate] day strings; the feed returns [feedIcs] verbatim
 * so UI tests can parse real-shaped ICS text without a server.
 */
class FakeSonarrCalendar(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    val calendarStore: MutableMap<Int, EpisodeResource> = linkedMapOf()
    val qualityDefinitionStore: MutableMap<Int, QualityDefinitionResource> = linkedMapOf()
    val qualityProfileStore: MutableMap<Int, QualityProfileResource> = linkedMapOf()
    val languageProfileStore: MutableMap<Int, LanguageProfileResource> = linkedMapOf()
    val releaseProfileStore: MutableMap<Int, ReleaseProfileResource> = linkedMapOf()
    val delayProfileStore: MutableMap<Int, DelayProfileResource> = linkedMapOf()
    val customFormatStore: MutableMap<Int, CustomFormatResource> = linkedMapOf()
    val languageStore: MutableMap<Int, LanguageResource> = linkedMapOf()
    var qualityDefinitionLimits: QualityDefinitionLimitsResource =
        QualityDefinitionLimitsResource()
    var customFormatSchemas: List<JsonObject> = emptyList()
    var feedIcs: String = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"
    var feedContentType: String = "text/calendar"

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missing(id: Any): Nothing = throw HttpException(
        Response.error<Any>(404, "Missing resource $id".toResponseBody())
    )

    private fun pathId(id: String): Int = id.toIntOrNull() ?: throw HttpException(
        Response.error<Any>(400, "Bad resource id $id".toResponseBody())
    )

    private fun <T> nextId(store: Map<Int, T>): Int =
        (store.keys.maxOrNull() ?: 0) + 1

    val calendar: CalendarApi = object : CalendarApi {
        override suspend fun list(
            start: String?,
            end: String?,
            unmonitored: Boolean?,
            includeSeries: Boolean?,
            includeEpisodeFile: Boolean?,
            includeEpisodeImages: Boolean?,
            tags: String?
        ): List<EpisodeResource> {
            before("Calendar.list")
            return calendarStore.values.filter { episode ->
                (start == null || !episode.airDate.isNullOrEmpty() &&
                    episode.airDate >= start) &&
                    (end == null || !episode.airDate.isNullOrEmpty() &&
                        episode.airDate <= end) &&
                    (unmonitored == true || episode.monitored)
            }
        }

        override suspend fun get(
            id: Int
        ): Response<EpisodeResource> {
            before("Calendar.get")
            return calendarStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing episode".toResponseBody())
        }
    }

    val calendarFeed: CalendarFeedApi = object : CalendarFeedApi {
        override suspend fun list(
            pastDays: Int?,
            futureDays: Int?,
            tags: String?,
            unmonitored: Boolean?,
            premieresOnly: Boolean?,
            asAllDay: Boolean?
        ): Response<ResponseBody> {
            before("CalendarFeed.list")
            val today = LocalDate.of(2026, 1, 15)
            val start = startBoundary(today, pastDays ?: 7).toString()
            val end = today.plusDays((futureDays ?: 28).toLong()).toString()
            val events = calendarStore.values.filter { episode ->
                val day = episode.airDate ?: return@filter false
                (premieresOnly != true || episode.episodeNumber == 1) &&
                    (unmonitored == true || episode.monitored)
            }.filter { episode -> episode.airDate!! in start..end }
            val body = buildIcs(events).toResponseBody(feedContentType.toMediaType())
            return Response.success(body)
        }
    }

    private fun startBoundary(today: LocalDate, pastDays: Int): LocalDate =
        if (pastDays < 0) today else today.minusDays(pastDays.toLong())

    private fun buildIcs(events: List<EpisodeResource>): String = buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//sonarr//fake//EN\r\n")
        events.forEach { episode ->
            append("BEGIN:VEVENT\r\n")
            append("UID:sonarr-${episode.id}\r\n")
            append("DTSTAMP:20260115T000000Z\r\n")
            append("SUMMARY:").append(episode.title.orEmpty()).append("\r\n")
            append("END:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    val qualityDefinition: QualityDefinitionApi = object : QualityDefinitionApi {
        override suspend fun list(): List<QualityDefinitionResource> {
            before("QualityDefinition.list")
            return qualityDefinitionStore.values.sortedBy { it.weight }
        }

        override suspend fun updateMany(
            body: List<QualityDefinitionResource>
        ): Unit {
            before("QualityDefinition.updateMany")
            body.forEach { definition ->
                qualityDefinitionStore[definition.id] = definition
            }
            return Unit
        }

        override suspend fun limits(): QualityDefinitionLimitsResource {
            before("QualityDefinition.limits")
            return qualityDefinitionLimits
        }

        override suspend fun update(
            id: String,
            body: QualityDefinitionResource
        ): QualityDefinitionResource {
            before("QualityDefinition.update")
            val key = pathId(id)
            if (key !in qualityDefinitionStore) missing(key)
            return body.copy(id = key).also { qualityDefinitionStore[key] = it }
        }

        override suspend fun get(
            id: Int
        ): Response<QualityDefinitionResource> {
            before("QualityDefinition.get")
            return qualityDefinitionStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing quality definition".toResponseBody())
        }
    }

    val qualityProfile: QualityProfileApi = object : QualityProfileApi {
        override suspend fun create(
            body: QualityProfileResource
        ): QualityProfileResource {
            before("QualityProfile.create")
            val id = nextId(qualityProfileStore)
            return body.copy(id = id).also { qualityProfileStore[id] = it }
        }

        override suspend fun list(): List<QualityProfileResource> {
            before("QualityProfile.list")
            return qualityProfileStore.values.toList()
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("QualityProfile.delete")
            qualityProfileStore.remove(id)
            return Unit
        }

        override suspend fun update(
            id: String,
            body: QualityProfileResource
        ): QualityProfileResource {
            before("QualityProfile.update")
            val key = pathId(id)
            if (key !in qualityProfileStore) missing(key)
            return body.copy(id = key).also { qualityProfileStore[key] = it }
        }

        override suspend fun get(
            id: Int
        ): Response<QualityProfileResource> {
            before("QualityProfile.get")
            return qualityProfileStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing quality profile".toResponseBody())
        }
    }

    val qualityProfileSchema: QualityProfileSchemaApi = object : QualityProfileSchemaApi {
        override suspend fun list(): QualityProfileResource {
            before("QualityProfileSchema.list")
            val definitions = qualityDefinitionStore.values.sortedBy { it.weight }
            return QualityProfileResource(
                name = "New Profile",
                upgradeAllowed = true,
                items = definitions.map { definition ->
                    QualityProfileQualityItemResource(
                        id = definition.quality.id,
                        name = definition.quality.name,
                        quality = definition.quality,
                        allowed = false
                    )
                },
                formatItems = customFormatStore.values.map { format ->
                    ProfileFormatItemResource(
                        format = format.id,
                        name = format.name
                    )
                }
            )
        }
    }

    val languageProfile: LanguageProfileApi = object : LanguageProfileApi {
        override suspend fun create(
            body: LanguageProfileResource
        ): LanguageProfileResource {
            before("LanguageProfile.create")
            val id = nextId(languageProfileStore)
            return body.copy(id = id).also { languageProfileStore[id] = it }
        }

        override suspend fun list(): List<LanguageProfileResource> {
            before("LanguageProfile.list")
            return languageProfileStore.values.toList()
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("LanguageProfile.delete")
            languageProfileStore.remove(id)
            return Unit
        }

        override suspend fun update(
            id: String,
            body: LanguageProfileResource
        ): LanguageProfileResource {
            before("LanguageProfile.update")
            val key = pathId(id)
            if (key !in languageProfileStore) missing(key)
            return body.copy(id = key).also { languageProfileStore[key] = it }
        }

        override suspend fun get(
            id: Int
        ): Response<LanguageProfileResource> {
            before("LanguageProfile.get")
            return languageProfileStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing language profile".toResponseBody())
        }
    }

    val languageProfileSchema: LanguageProfileSchemaApi = object : LanguageProfileSchemaApi {
        override suspend fun list(): LanguageProfileResource {
            before("LanguageProfileSchema.list")
            return LanguageProfileResource(
                name = "New Language Profile",
                upgradeAllowed = true,
                languages = languageStore.values.map { language ->
                    LanguageProfileItemResource(
                        language = Language(id = language.id, name = language.name)
                    )
                }
            )
        }
    }

    val releaseProfile: ReleaseProfileApi = object : ReleaseProfileApi {
        override suspend fun create(
            body: ReleaseProfileResource
        ): ReleaseProfileResource {
            before("ReleaseProfile.create")
            val id = nextId(releaseProfileStore)
            return body.copy(id = id).also { releaseProfileStore[id] = it }
        }

        override suspend fun list(): List<ReleaseProfileResource> {
            before("ReleaseProfile.list")
            return releaseProfileStore.values.toList()
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("ReleaseProfile.delete")
            releaseProfileStore.remove(id)
            return Unit
        }

        override suspend fun update(
            id: String,
            body: ReleaseProfileResource
        ): ReleaseProfileResource {
            before("ReleaseProfile.update")
            val key = pathId(id)
            if (key !in releaseProfileStore) missing(key)
            return body.copy(id = key).also { releaseProfileStore[key] = it }
        }

        override suspend fun get(
            id: Int
        ): Response<ReleaseProfileResource> {
            before("ReleaseProfile.get")
            return releaseProfileStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing release profile".toResponseBody())
        }
    }

    val delayProfile: DelayProfileApi = object : DelayProfileApi {
        override suspend fun create(
            body: DelayProfileResource
        ): DelayProfileResource {
            before("DelayProfile.create")
            val id = nextId(delayProfileStore)
            return body.copy(id = id).also { delayProfileStore[id] = it }
        }

        override suspend fun list(): List<DelayProfileResource> {
            before("DelayProfile.list")
            return delayProfileStore.values.sortedBy { it.order }
        }

        override suspend fun reorder(
            id: Int,
            after: Int?
        ): List<DelayProfileResource> {
            before("DelayProfile.reorder")
            val moving = delayProfileStore[id] ?: missing(id)
            val ordered = delayProfileStore.values.sortedBy { it.order }
                .filterNot { it.id == id }.toMutableList()
            val index = if (after == null) 0 else {
                val anchor = ordered.indexOfFirst { it.id == after }
                if (anchor < 0) missing(after)
                anchor + 1
            }
            ordered.add(index, moving)
            ordered.forEachIndexed { position, profile ->
                delayProfileStore[profile.id] = profile.copy(order = position + 1)
            }
            return delayProfileStore.values.sortedBy { it.order }
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("DelayProfile.delete")
            delayProfileStore.remove(id)
            return Unit
        }

        override suspend fun update(
            id: String,
            body: DelayProfileResource
        ): DelayProfileResource {
            before("DelayProfile.update")
            val key = pathId(id)
            if (key !in delayProfileStore) missing(key)
            return body.copy(id = key).also { delayProfileStore[key] = it }
        }

        override suspend fun get(
            id: Int
        ): Response<DelayProfileResource> {
            before("DelayProfile.get")
            return delayProfileStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing delay profile".toResponseBody())
        }
    }

    val customFormat: CustomFormatApi = object : CustomFormatApi {
        override suspend fun list(): List<CustomFormatResource> {
            before("CustomFormat.list")
            return customFormatStore.values.toList()
        }

        override suspend fun create(
            body: CustomFormatResource
        ): CustomFormatResource {
            before("CustomFormat.create")
            val id = nextId(customFormatStore)
            return body.copy(id = id).also { customFormatStore[id] = it }
        }

        override suspend fun updateBulk(
            body: CustomFormatBulkResource
        ): CustomFormatResource {
            before("CustomFormat.updateBulk")
            val ids = body.ids.orEmpty()
            if (ids.isEmpty()) missing("bulk")
            ids.forEach { id ->
                val row = customFormatStore[id] ?: missing(id)
                customFormatStore[id] = row.copy(
                    includeCustomFormatWhenRenaming =
                    body.includeCustomFormatWhenRenaming
                        ?: row.includeCustomFormatWhenRenaming
                )
            }
            return customFormatStore.values.first()
        }

        override suspend fun deleteBulk(
            body: CustomFormatBulkResource
        ): Unit {
            before("CustomFormat.deleteBulk")
            body.ids.orEmpty().forEach(customFormatStore::remove)
            return Unit
        }

        override suspend fun schema(): List<JsonObject> {
            before("CustomFormat.schema")
            return customFormatSchemas
        }

        override suspend fun update(
            id: String,
            body: CustomFormatResource
        ): CustomFormatResource {
            before("CustomFormat.update")
            val key = pathId(id)
            if (key !in customFormatStore) missing(key)
            return body.copy(id = key).also { customFormatStore[key] = it }
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("CustomFormat.delete")
            customFormatStore.remove(id)
            return Unit
        }

        override suspend fun get(
            id: Int
        ): Response<CustomFormatResource> {
            before("CustomFormat.get")
            return customFormatStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing custom format".toResponseBody())
        }
    }

    val language: LanguageApi = object : LanguageApi {
        override suspend fun list(): List<LanguageResource> {
            before("Language.list")
            return languageStore.values.sortedBy { it.id }
        }

        override suspend fun get(
            id: Int
        ): Response<LanguageResource> {
            before("Language.get")
            return languageStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing language".toResponseBody())
        }
    }
}
