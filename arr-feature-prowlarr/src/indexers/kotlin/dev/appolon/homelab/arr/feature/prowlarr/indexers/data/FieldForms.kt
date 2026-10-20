package dev.appolon.homelab.arr.feature.prowlarr.indexers.data

import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.PrivacyLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** How one dynamic provider field renders in the editor form. */
enum class FieldRenderKind { TEXT, NUMBER, CHECKBOX, PASSWORD, SELECT, TAG_SELECT }

/**
 * Pure helpers over the generic [Field] model shared by indexers, indexer
 * proxies, and applications. The editable form is a `Map<String,
 * JsonElement>` keyed by field name; converting to and from the wire value
 * lives here so screens and tests share one definition.
 */
object FieldForms {

    /** Field names the Prowlarr UI groups under a seed criteria section. */
    private const val SEED_CRITERIA_PREFIX = "seedCriteria."

    fun isEditable(field: Field): Boolean =
        field.name != null && field.hidden != "hidden" && field.hidden != "hiddenIfNotEnabled"

    fun isSeedCriteria(field: Field): Boolean =
        field.name?.startsWith(SEED_CRITERIA_PREFIX) == true

    /** Chooses the editor widget from the declared type, privacy, and options. */
    fun renderKind(field: Field): FieldRenderKind = when {
        field.type == "checkbox" -> FieldRenderKind.CHECKBOX
        field.privacy == PrivacyLevel.PASSWORD ||
            field.privacy == PrivacyLevel.API_KEY ||
            field.type == "password" -> FieldRenderKind.PASSWORD
        field.type == "number" -> FieldRenderKind.NUMBER
        field.type == "select" || field.selectOptions?.isNotEmpty() == true -> FieldRenderKind.SELECT
        field.type?.contains("tag", ignoreCase = true) == true -> FieldRenderKind.TAG_SELECT
        else -> FieldRenderKind.TEXT
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

    /** Tag select values arrive as arrays of tag ids, sometimes bare primitives. */
    fun idsOf(value: JsonElement?): List<Int> = when (value) {
        null, is JsonNull -> emptyList()
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        is JsonPrimitive -> listOfNotNull(value.intOrNull)
        else -> emptyList()
    }

    fun text(value: String): JsonElement = JsonPrimitive(value)

    fun bool(value: Boolean): JsonElement = JsonPrimitive(value)

    fun int(value: Int?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value)

    fun double(value: Double?): JsonElement =
        if (value == null) JsonNull else JsonPrimitive(value)

    fun ids(values: List<Int>): JsonElement = JsonArray(values.map { JsonPrimitive(it) })

    fun stringValue(field: Field, values: Map<String, JsonElement>): String =
        textOf(values[field.name])
}

/** Reads and writes the per-indexer category selection on the resource capabilities. */
object IndexerCategories {

    /** Flattens the default category tree into one list, parents first. */
    fun flatten(categories: List<IndexerCategory>): List<IndexerCategory> =
        categories.flatMap { listOf(it) + flatten(it.subCategories.orEmpty()) }

    /** Category ids this indexer covers, capabilities flattened. */
    fun selectedIds(resource: IndexerResource): Set<Int> =
        flatten(resource.capabilities.categories.orEmpty()).map { it.id }.toSet()

    /**
     * Writes the selection back as a flat category list. Names resolve from
     * the server defaults so the payload stays self describing; the server
     * rebuilds the tree.
     */
    fun withSelectedIds(
        resource: IndexerResource,
        ids: Set<Int>,
        defaults: List<IndexerCategory>,
    ): IndexerResource {
        val known = flatten(defaults).associateBy { it.id }
        val categories = ids.sorted().map { id ->
            IndexerCategory(id = id, name = known[id]?.name)
        }
        return resource.copy(
            capabilities = resource.capabilities.copy(categories = categories),
        )
    }
}
