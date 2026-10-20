package dev.appolon.homelab.arr.feature.lidarr.settings.system.data

import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.lidarr.model.Field
import dev.appolon.homelab.arr.api.lidarr.model.SelectOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The condition catalog for the auto tagging editor. Lidarr's OpenAPI spec
 * leaves the `/api/v1/autotagging/schema` endpoint untyped, so the
 * committed API interface returns a raw JSON object; [fromSchema] parses
 * the live document when the server is reachable, and the static
 * [entries] list (implementation names and wire field names verified
 * against Lidarr's AutoTagging specifications) seeds the picker offline.
 * Conditions loaded from a rule keep whatever fields the server sent, so
 * unknown specifications still round-trip untouched.
 */
object LidarrAutoTaggingConditions {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Condition templates read from the raw schema object, in listed
     * order. A document carrying a "specifications" array yields one entry
     * per element, and a bare specification object yields itself, so both
     * the server's shape and smaller payloads work. Entries that fail to
     * decode are skipped.
     */
    fun fromSchema(schema: JsonObject): List<AutoTaggingSpecificationSchema> {
        val sources = when (val specs = schema["specifications"]) {
            is JsonArray -> specs
            else -> JsonArray(listOf(schema))
        }
        return sources.mapNotNull { element ->
            try {
                json.decodeFromJsonElement(AutoTaggingSpecificationSchema.serializer(), element)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    private fun numberField(name: String, label: String, value: Int? = null) = Field(
        order = 1,
        name = name,
        label = label,
        value = value?.let { JsonPrimitive(it) } ?: JsonNull,
        type = "number",
    )

    private fun tagField(name: String, label: String) = Field(
        order = 1,
        name = name,
        label = label,
        value = JsonArray(emptyList()),
        type = "tag",
    )

    private fun selectField(
        name: String,
        label: String,
        options: List<Pair<String, Int>>,
        selected: Int = options.first().second,
    ) = Field(
        order = 1,
        name = name,
        label = label,
        value = JsonPrimitive(selected),
        type = "select",
        selectOptions = options.map { (text, id) -> SelectOption(value = id, name = text) },
    )

    private fun condition(
        implementation: String,
        implementationName: String,
        fields: List<Field> = emptyList(),
    ) = AutoTaggingSpecificationSchema(
        implementation = implementation,
        name = implementationName,
        implementationName = implementationName,
        fields = fields,
    )

    /** Every condition Lidarr v2 ships, ordered as the server's schema lists them. */
    val entries: List<AutoTaggingSpecificationSchema> = listOf(
        condition("StatusSpecification", "Status", listOf(
            selectField(
                "status",
                "Status",
                listOf("Continuing" to 0, "Ended" to 1, "Deleted" to 2),
            ),
        )),
        condition("QualityProfileSpecification", "Quality Profile", listOf(
            numberField("value", "Quality Profile"),
        )),
        condition("MetadataProfileSpecification", "Metadata Profile", listOf(
            numberField("value", "Metadata Profile"),
        )),
        condition("RootFolderSpecification", "Root Folder", listOf(
            Field(
                order = 1,
                name = "value",
                label = "Root Folder",
                value = JsonPrimitive(""),
                type = "rootFolder",
            ),
        )),
        condition("GenreSpecification", "Genre", listOf(tagField("value", "Genre(s)"))),
        condition("MonitoredSpecification", "Monitored"),
        condition("TagSpecification", "Tag", listOf(tagField("value", "Tag"))),
    )

    /** One catalog entry by implementation name, or null for unknown ones. */
    fun byImplementation(implementation: String): AutoTaggingSpecificationSchema? =
        entries.firstOrNull { it.implementation == implementation }
}
