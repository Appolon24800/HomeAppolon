package dev.appolon.homelab.arr.feature.lidarr.settings.quality.data

import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.lidarr.model.Field
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFormatConditionsTest {

    @Test
    fun `release name conditions round trip as strings`() {
        val condition = CustomFormatCondition(
            kind = CustomFormatConditionKind.RELEASE_NAME,
            name = "Has FLAC",
            negate = true,
            value = "flac",
        )
        val schema = CustomFormatConditions.toSchema(condition)

        assertEquals("ReleaseSpecification", schema.implementation)
        assertEquals("Has FLAC", schema.name)
        assertTrue(schema.negate)
        assertEquals("flac", schema.fields!!.first().value!!.let { (it as JsonPrimitive).content })

        val parsed = CustomFormatConditions.fromSchema(schema)
        assertEquals(condition.copy(), parsed)
    }

    @Test
    fun `numeric conditions round trip through integer strings`() {
        val condition = CustomFormatCondition(
            kind = CustomFormatConditionKind.LANGUAGE,
            value = "5",
        )
        val schema = CustomFormatConditions.toSchema(condition)

        val parsed = CustomFormatConditions.fromSchema(schema)
        assertEquals("5", parsed!!.value)
    }

    @Test
    fun `size range conditions carry both bounds`() {
        val condition = CustomFormatCondition(
            kind = CustomFormatConditionKind.SIZE_RANGE,
            minSize = "10",
            maxSize = "250.5",
        )
        val schema = CustomFormatConditions.toSchema(condition)
        val fields = schema.fields!!.associate { it.name to it.value }

        assertEquals(listOf("min", "max"), fields.keys.toList())
        assertEquals("10", CustomFormatConditions.fromSchema(schema)!!.minSize)
        assertEquals("250.5", CustomFormatConditions.fromSchema(schema)!!.maxSize)
        assertTrue(condition.isComplete())
    }

    @Test
    fun `unknown implementations pass through untouched`() {
        val schema = CustomFormatSpecificationSchema(
            name = "Future spec",
            implementation = "SomeFutureSpecification",
            fields = listOf(Field(order = 1, name = "value", value = JsonPrimitive(1))),
        )

        assertNull(CustomFormatConditions.fromSchema(schema))
    }

    @Test
    fun `valueFields lists the wire layout per kind`() {
        assertEquals(
            listOf("value"),
            CustomFormatConditions.valueFields(CustomFormatConditionKind.INDEXER_FLAG),
        )
        assertEquals(
            listOf("min", "max"),
            CustomFormatConditions.valueFields(CustomFormatConditionKind.SIZE_RANGE),
        )
    }

    @Test
    fun `regexMatch distinguishes match, miss, invalid and blank`() {
        assertEquals(true, CustomFormatConditions.regexMatch("flac", "Artist-Album-FLAC-2024"))
        assertEquals(false, CustomFormatConditions.regexMatch("mp3", "Artist-Album-FLAC-2024"))
        assertNull(CustomFormatConditions.regexMatch("([unclosed", "sample"))
        assertNull(CustomFormatConditions.regexMatch("", "sample"))
    }

    @Test
    fun `a condition without values is incomplete`() {
        assertFalse(
            CustomFormatCondition(kind = CustomFormatConditionKind.LANGUAGE, value = " ").isComplete(),
        )
        assertFalse(
            CustomFormatCondition(kind = CustomFormatConditionKind.SIZE_RANGE).isComplete(),
        )
    }
}
