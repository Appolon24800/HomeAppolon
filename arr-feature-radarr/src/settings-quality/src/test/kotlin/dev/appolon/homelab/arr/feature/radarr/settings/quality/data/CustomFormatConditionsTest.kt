package dev.appolon.homelab.arr.feature.radarr.settings.quality.data

import dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.Field
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFormatConditionsTest {

    @Test
    fun `release name condition round trips with regex text value`() {
        val condition = CustomFormatCondition(
            kind = CustomFormatConditionKind.RELEASE_NAME,
            name = "Remux tag",
            negate = true,
            required = true,
            value = "-REMUX",
        )
        val schema = CustomFormatConditions.toSchema(condition)
        assertEquals("ReleaseTitleSpecification", schema.implementation)
        assertEquals(JsonPrimitive("-REMUX"), schema.fields!!.first().value)
        val back = CustomFormatConditions.fromSchema(schema)
        assertEquals(condition, back)
    }

    @Test
    fun `language and indexer flag conditions keep numeric wire values`() {
        val language = CustomFormatConditions.toSchema(
            CustomFormatCondition(kind = CustomFormatConditionKind.LANGUAGE, value = "1"),
        )
        assertEquals("LanguageSpecification", language.implementation)
        assertEquals(JsonPrimitive(1.0), language.fields!!.first().value)

        val flag = CustomFormatConditions.toSchema(
            CustomFormatCondition(kind = CustomFormatConditionKind.INDEXER_FLAG, value = "2"),
        )
        assertEquals("IndexerFlagSpecification", flag.implementation)
        assertEquals(JsonPrimitive(2.0), flag.fields!!.first().value)
    }

    @Test
    fun `quality modifier condition encodes the dropdown ordinal`() {
        val schema = CustomFormatConditions.toSchema(
            CustomFormatCondition(kind = CustomFormatConditionKind.QUALITY_MODIFIER, value = "4"),
        )
        assertEquals("QualityModifierSpecification", schema.implementation)
        assertEquals(JsonPrimitive(4.0), schema.fields!!.first().value)
    }

    @Test
    fun `size range condition round trips decimal bounds as doubles`() {
        val condition = CustomFormatCondition(
            kind = CustomFormatConditionKind.SIZE_RANGE,
            minSize = "2.5",
            maxSize = "40",
        )
        val schema = CustomFormatConditions.toSchema(condition)
        val fields = schema.fields.orEmpty()
        val min = fields.first { it.name == "min" }
        val max = fields.first { it.name == "max" }
        assertEquals(JsonPrimitive(2.5), min.value)
        assertEquals(JsonPrimitive(40.0), max.value)

        val back = CustomFormatConditions.fromSchema(schema)!!
        assertEquals("2.5", back.minSize)
        assertEquals("40", back.maxSize)
        assertTrue(back.isComplete())
    }

    @Test
    fun `unknown implementations parse to null so they pass through untouched`() {
        val schema = CustomFormatSpecificationSchema(
            implementation = "YearSpecification",
            fields = listOf(Field(name = "min", value = JsonPrimitive(2020))),
        )
        assertNull(CustomFormatConditions.fromSchema(schema))
    }

    @Test
    fun `regexMatch is case insensitive and rejects invalid patterns`() {
        assertEquals(java.lang.Boolean.TRUE, CustomFormatConditions.regexMatch("remux", "Movie.2024.REMUX.1080p"))
        assertEquals(java.lang.Boolean.FALSE, CustomFormatConditions.regexMatch("proper", "Movie.2024.1080p"))
        assertNull(CustomFormatConditions.regexMatch("([invalid", "sample"))
        assertNull(CustomFormatConditions.regexMatch("", "sample"))
    }

    @Test
    fun `blank single value conditions are incomplete`() {
        assertFalse(
            CustomFormatCondition(kind = CustomFormatConditionKind.EDITION, value = "  ").isComplete(),
        )
        assertTrue(
            CustomFormatCondition(kind = CustomFormatConditionKind.EDITION, value = "IMAX").isComplete(),
        )
        assertFalse(
            CustomFormatCondition(kind = CustomFormatConditionKind.SIZE_RANGE, minSize = "", maxSize = "")
                .isComplete(),
        )
    }

    @Test
    fun `decimal values round to integer text when whole`() {
        val schema = CustomFormatSpecificationSchema(
            implementation = "LanguageSpecification",
            fields = listOf(Field(name = "value", value = JsonPrimitive(3.0))),
        )
        val back = CustomFormatConditions.fromSchema(schema)
        assertNotNull(back)
        assertEquals("3", back!!.value)
    }
}
