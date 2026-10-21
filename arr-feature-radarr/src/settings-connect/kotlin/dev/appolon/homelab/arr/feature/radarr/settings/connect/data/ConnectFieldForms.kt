package dev.appolon.homelab.arr.feature.radarr.settings.connect.data

import dev.appolon.homelab.arr.api.radarr.model.Field
import dev.appolon.homelab.arr.api.radarr.model.PrivacyLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** How one dynamic provider field renders in the editor forms. */
enum class ConnectFieldRenderKind { TEXT, NUMBER, CHECKBOX, PASSWORD, SELECT, TAG_SELECT }

/**
 * Pure helpers over Radarr's generic [Field] model, shared by the indexer,
 * download client, import list, notification, and metadata editors. The
 * pattern mirrors the committed Prowlarr indexers FieldForms renderer: the
 * editable form is a `Map<String, JsonElement>` keyed by field name, and
 * every conversion between wire values and editor state lives here so
 * screens and tests share one definition.
 */
object ConnectFieldForms {

    /** Newznab/torrent categories arrive as a multi-select field with this name. */
    const val CATEGORIES_FIELD = "categories"

    /** Torrent indexers group seed constraints under this prefix. */
    private const val SEED_CRITERIA_PREFIX = "seedCriteria."

    fun isEditable(field: Field): Boolean =
        field.name != null && field.hidden != "hidden" && field.hidden != "hiddenIfNotEnabled"

    fun isSeedCriteria(field: Field): Boolean =
        field.name?.startsWith(SEED_CRITERIA_PREFIX) == true

    /** Seed ratio and seed time read out of the raw field values for list rows. */
    fun seedRatio(fields: List<Field>?): Double? =
        (fields?.firstOrNull { it.name == "seedCriteria.seedRatio" }?.value as? JsonPrimitive)
            ?.doubleOrNull

    fun seedTime(fields: List<Field>?): Double? =
        (fields?.firstOrNull { it.name == "seedCriteria.seedTime" }?.value as? JsonPrimitive)
            ?.doubleOrNull

    /** Category ids selected in the categories field, tolerating bare numbers. */
    fun categoryIds(fields: List<Field>?): List<Int> =
        idsOf(fields?.firstOrNull { it.name == CATEGORIES_FIELD }?.value)

    /** Chooses the editor widget from the declared type, privacy, and options. */
    fun renderKind(field: Field): ConnectFieldRenderKind = when {
        field.type == "checkbox" -> ConnectFieldRenderKind.CHECKBOX
        field.privacy == PrivacyLevel.PASSWORD ||
            field.privacy == PrivacyLevel.API_KEY ||
            field.type == "password" -> ConnectFieldRenderKind.PASSWORD
        field.type == "number" -> ConnectFieldRenderKind.NUMBER
        field.type == "select" || field.selectOptions?.isNotEmpty() == true -> ConnectFieldRenderKind.SELECT
        field.type?.contains("tag", ignoreCase = true) == true ||
            field.name == CATEGORIES_FIELD -> ConnectFieldRenderKind.TAG_SELECT
        else -> ConnectFieldRenderKind.TEXT
    }

    /** Snapshot of the current field values, skipping non-editable fields. */
    fun editableValues(fields: List<Field>?): Map<String, JsonElement> =
        fields.orEmpty().filter(::isEditable).associate { field ->
            field.name!! to (field.value ?: JsonNull)
        }

    /** Writes edited values back into the field list, preserving everything else. */
    fun applyValues(fields: List<Field>?, values: Map<String, JsonElement>): List<Field> =
        fields.orEmpty().map { field ->
            val name = field.name ?: return@map field
            if (values.containsKey(name)) field.copy(value = values.getValue(name)) else field
        }

    fun textOf(value: JsonElement?): String = when (value) {
        null, is JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    fun boolOf(value: JsonElement?): Boolean = (value as? JsonPrimitive)?.booleanOrNull ?: false

    fun intOf(value: JsonElement?): Int? = (value as? JsonPrimitive)?.intOrNull

    fun doubleOf(value: JsonElement?): Double? = (value as? JsonPrimitive)?.doubleOrNull

    /** Tag and category selects arrive as arrays of ids, sometimes bare primitives. */
    fun idsOf(value: JsonElement?): List<Int> = when (value) {
        null, is JsonNull -> emptyList()
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        is JsonPrimitive -> listOfNotNull(value.intOrNull)
        else -> emptyList()
    }

    fun text(value: String): JsonElement = JsonPrimitive(value)

    fun bool(value: Boolean): JsonElement = JsonPrimitive(value)

    fun int(value: Int?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)

    fun double(value: Double?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)

    fun ids(values: List<Int>): JsonElement = JsonArray(values.map { JsonPrimitive(it) })
}
