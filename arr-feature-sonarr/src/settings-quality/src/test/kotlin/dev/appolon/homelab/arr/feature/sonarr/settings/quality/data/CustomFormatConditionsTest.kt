package dev.appolon.homelab.arr.feature.sonarr.settings.quality.data

import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.sonarr.model.Field
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFormatConditionsTest {

    @Test
    fun `round trips a release name regex condition`() {
        val condition = CustomFormatCondition(
            kind = CustomFormatConditionKind.RELEASE_NAME,
            name = "Remux",
            value = "remux",
        )
        val schema = CustomFormatConditions.toSchema(condition)
        val parsed = CustomFormatConditions.fromSchema(schema)!!

        assertEquals("ReleaseSpecification", schema.implementation)
        assertEquals("remux", (schema.fields!!.first().value as JsonPrimitive).content)
        assertEquals(condition.copy(), parsed)
    }

    @Test
    fun `round trips a language condition as a numeric id`() {
        val schema = CustomFormatConditions.toSchema(
            CustomFormatCondition(kind = CustomFormatConditionKind.LANGUAGE, value = "1"),
        )
        val parsed = CustomFormatConditions.fromSchema(schema)!!

        assertEquals(1.0, (schema.fields!!.first().value as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals("1", parsed.value)
        assertTrue(parsed.isComplete())
    }

    @Test
    fun `round trips a size range keeping integers clean`() {
        val schema = CustomFormatConditions.toSchema(
            CustomFormatCondition(
                kind = CustomFormatConditionKind.SIZE_RANGE,
                minSize = "5",
                maxSize = "20.5",
            ),
        )
        val parsed = CustomFormatConditions.fromSchema(schema)!!

        assertEquals(listOf("min", "max"), CustomFormatConditions.valueFields(CustomFormatConditionKind.SIZE_RANGE))
        assertEquals("5", parsed.minSize)
        assertEquals("20.5", parsed.maxSize)
        assertTrue(parsed.isComplete())
    }

    @Test
    fun `unknown implementations pass through as null instead of corrupting`() {
        val parsed = CustomFormatConditions.fromSchema(
            CustomFormatSpecificationSchema(implementation = "FutureSpecification"),
        )
        assertNull(parsed)
    }

    @Test
    fun `blank conditions are incomplete`() {
        assertFalse(
            CustomFormatCondition(kind = CustomFormatConditionKind.INDEXER_FLAG).isComplete(),
        )
        assertFalse(
            CustomFormatCondition(kind = CustomFormatConditionKind.SIZE_RANGE).isComplete(),
        )
    }

    @Test
    fun `regexMatch is case-insensitive and reports invalid patterns as null`() {
        assertEquals(true, CustomFormatConditions.regexMatch("remux", "Series.S01.REMUX.1080p"))
        assertEquals(false, CustomFormatConditions.regexMatch("remux", "Series.S01.WEBRip"))
        assertNull(CustomFormatConditions.regexMatch("(unclosed", "sample"))
        assertNull(CustomFormatConditions.regexMatch("", "sample"))
    }

    @Test
    fun `field values survive a decimal wire form`() {
        val parsed = CustomFormatConditions.fromSchema(
            CustomFormatSpecificationSchema(
                implementation = "LanguageSpecification",
                fields = listOf(Field(name = "value", value = JsonPrimitive(3))),
            ),
        )!!
        assertEquals("3", parsed.value)
    }
}
