package dev.appolon.homelab.arr.feature.lidarr.settings.quality.data

import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.lidarr.model.Field
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The condition kinds the Lidarr custom format builder edits, one per
 * specification implementation Lidarr ships. The wire format is
 * schema-free (`fields` carry raw JSON values), so each kind owns its
 * implementation name and field layout here instead of in the api module.
 */
enum class CustomFormatConditionKind(val wireImplementation: String) {
    RELEASE_NAME("ReleaseSpecification"),
    LANGUAGE("LanguageSpecification"),
    INDEXER_FLAG("IndexerFlagSpecification"),
    SIZE_RANGE("SizeSpecification"),
}

/**
 * Editable draft of one custom format condition. Values are kept as strings
 * so partially typed numbers never crash the editor; each kind defines which
 * fields it reads through [CustomFormatConditions.valueFields].
 */
data class CustomFormatCondition(
    val kind: CustomFormatConditionKind,
    val name: String = "",
    val negate: Boolean = false,
    val required: Boolean = false,
    /** Single-value conditions: regex, language id, flag id. */
    val value: String = "",
    /** Size range bounds in megabytes, used only by [CustomFormatConditionKind.SIZE_RANGE]. */
    val minSize: String = "",
    val maxSize: String = "",
) {
    /** True when the draft carries at least one usable value for its kind. */
    fun isComplete(): Boolean = when (kind) {
        CustomFormatConditionKind.SIZE_RANGE ->
            minSize.toDoubleOrNull() != null || maxSize.toDoubleOrNull() != null
        else -> value.isNotBlank()
    }
}

/** Condition mapping helpers shared by the editor and the tests. */
object CustomFormatConditions {

    /** Field names each kind writes on the wire, in order. */
    fun valueFields(kind: CustomFormatConditionKind): List<String> = when (kind) {
        CustomFormatConditionKind.SIZE_RANGE -> listOf("min", "max")
        else -> listOf("value")
    }

    /** Builds the wire schema for a draft. Numeric kinds encode numbers, sizes doubles. */
    fun toSchema(condition: CustomFormatCondition): CustomFormatSpecificationSchema {
        val fields = when (condition.kind) {
            CustomFormatConditionKind.SIZE_RANGE -> listOf(
                numberField("min", condition.minSize),
                numberField("max", condition.maxSize),
            )

            CustomFormatConditionKind.RELEASE_NAME ->
                listOf(Field(order = 1, name = "value", value = JsonPrimitive(condition.value)))

            else -> listOf(numberField("value", condition.value))
        }
        return CustomFormatSpecificationSchema(
            name = condition.name.ifBlank { condition.kind.wireImplementation },
            implementation = condition.kind.wireImplementation,
            negate = condition.negate,
            required = condition.required,
            fields = fields,
        )
    }

    /**
     * Parses a wire schema back into a draft. Returns null for unknown
     * implementations so a server-side condition added after this build is
     * carried untouched instead of corrupted.
     */
    fun fromSchema(schema: CustomFormatSpecificationSchema): CustomFormatCondition? {
        val kind = CustomFormatConditionKind.entries.firstOrNull {
            it.wireImplementation == schema.implementation
        } ?: return null
        val byName = schema.fields.orEmpty().associate { it.name to it.value }
        return when (kind) {
            CustomFormatConditionKind.SIZE_RANGE -> CustomFormatCondition(
                kind = kind,
                name = schema.name.orEmpty(),
                negate = schema.negate,
                required = schema.required,
                minSize = byName["min"].decimalOrBlank(),
                maxSize = byName["max"].decimalOrBlank(),
            )

            CustomFormatConditionKind.RELEASE_NAME -> CustomFormatCondition(
                kind = kind,
                name = schema.name.orEmpty(),
                negate = schema.negate,
                required = schema.required,
                value = (byName["value"] as? JsonPrimitive)?.content ?: "",
            )

            else -> CustomFormatCondition(
                kind = kind,
                name = schema.name.orEmpty(),
                negate = schema.negate,
                required = schema.required,
                value = byName["value"].decimalOrBlank(),
            )
        }
    }

    /**
     * Tests a release-title regex against a sample the way Lidarr matches
     * release names: case-insensitive, first match wins. Returns null when
     * the pattern is invalid or blank, so the UI can tell the two apart.
     */
    fun regexMatch(pattern: String, sample: String): Boolean? {
        if (pattern.isBlank()) return null
        val regex = try {
            pattern.toRegex(RegexOption.IGNORE_CASE)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return regex.containsMatchIn(sample)
    }

    private fun numberField(name: String, raw: String): Field = Field(
        order = if (name == "max") 2 else 1,
        name = name,
        value = JsonPrimitive(raw.toDoubleOrNull() ?: 0.0),
    )

    private fun JsonElement?.decimalOrBlank(): String = when (this) {
        is JsonPrimitive -> when {
            content.toDoubleOrNull() != null -> {
                val asDouble = content.toDouble()
                // Integers stay integers; the editor writes "5", not "5.0".
                if (asDouble == asDouble.toInt().toDouble()) {
                    asDouble.toInt().toString()
                } else {
                    asDouble.toString()
                }
            }

            else -> content
        }

        else -> ""
    }
}
