package dev.appolon.homelab.arr.feature.sonarr.settings.system.data

import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.SelectOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The condition catalog for the auto tagging editor. Sonarr's OpenAPI spec
 * leaves the `/autotagging/schema` endpoint untyped, so the committed API
 * interface cannot return parsed templates; this static list carries the
 * specification set Sonarr's server implements (implementation names and
 * wire field names verified against Sonarr's AutoTagging specifications)
 * and the editor seeds new conditions from it. Conditions loaded from a
 * rule keep whatever fields the server sent, so unknown specifications
 * still round-trip untouched.
 */
object SonarrAutoTaggingConditions {

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

    /** Every condition Sonarr v4 ships, ordered as the server's schema lists them. */
    val entries: List<AutoTaggingSpecificationSchema> = listOf(
        condition("SeriesTypeSpecification", "Series Type", listOf(
            selectField(
                "value",
                "Series type",
                listOf("Standard" to 0, "Daily" to 1, "Anime" to 2),
            ),
        )),
        condition("QualityProfileSpecification", "Quality Profile", listOf(
            numberField("value", "Quality profile id"),
        )),
        condition("RootFolderSpecification", "Root Folder", listOf(
            Field(
                order = 1,
                name = "value",
                label = "Root folder path",
                value = JsonPrimitive(""),
                type = "rootFolder",
            ),
        )),
        condition("GenreSpecification", "Genre", listOf(tagField("value", "Genres"))),
        condition("NetworkSpecification", "Network", listOf(tagField("value", "Networks"))),
        condition("OriginalCountrySpecification", "Original Country", listOf(
            tagField("value", "Countries"),
        )),
        condition("OriginalLanguageSpecification", "Original Language", listOf(
            numberField("value", "Original language id"),
        )),
        condition("StatusSpecification", "Status", listOf(
            selectField(
                "status",
                "Series status",
                listOf("Continuing" to 0, "Ended" to 1, "Upcoming" to 2, "Deleted" to -1),
            ),
        )),
        condition("TagSpecification", "Tag", listOf(numberField("value", "Tag id"))),
        condition("YearSpecification", "Year", listOf(
            numberField("min", "Minimum year", 1900),
            numberField("max", "Maximum year", 2100),
        )),
        condition("MonitoredSpecification", "Monitored"),
    )

    /** One catalog entry by implementation name, or null for unknown ones. */
    fun byImplementation(implementation: String): AutoTaggingSpecificationSchema? =
        entries.firstOrNull { it.implementation == implementation }
}
