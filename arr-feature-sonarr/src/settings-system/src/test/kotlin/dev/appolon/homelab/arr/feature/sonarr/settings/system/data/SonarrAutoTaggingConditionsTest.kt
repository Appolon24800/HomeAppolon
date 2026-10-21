package dev.appolon.homelab.arr.feature.sonarr.settings.system.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrAutoTaggingConditionsTest {

    @Test
    fun `every entry carries a distinct implementation and a display name`() {
        val implementations = SonarrAutoTaggingConditions.entries.map { it.implementation }
        assertEquals(implementations.size, implementations.distinct().size)
        SonarrAutoTaggingConditions.entries.forEach { entry ->
            assertTrue(entry.implementation!!.endsWith("Specification"))
            assertTrue(entry.implementationName!!.isNotBlank())
        }
    }

    @Test
    fun `year condition carries min and max number fields`() {
        val year = SonarrAutoTaggingConditions.byImplementation("YearSpecification")!!
        val fields = year.fields!!.associateBy { it.name }
        assertNotNull(fields["min"])
        assertNotNull(fields["max"])
        assertEquals("number", fields["min"]!!.type)
    }

    @Test
    fun `series type condition offers the documented select options`() {
        val seriesType = SonarrAutoTaggingConditions.byImplementation("SeriesTypeSpecification")!!
        val field = seriesType.fields!!.single()
        val values = field.selectOptions!!.mapNotNull { it.name }
        assertEquals(listOf("Standard", "Daily", "Anime"), values)
        assertEquals(0, (field.value as JsonPrimitive).intOrNull)
    }

    @Test
    fun `status condition includes deleted at minus one`() {
        val status = SonarrAutoTaggingConditions.byImplementation("StatusSpecification")!!
        val field = status.fields!!.single()
        val deleted = field.selectOptions!!.first { it.name == "Deleted" }
        assertEquals(-1, deleted.value)
    }

    @Test
    fun `genre condition starts with an empty tag list`() {
        val genre = SonarrAutoTaggingConditions.byImplementation("GenreSpecification")!!
        val field = genre.fields!!.single()
        assertTrue((field.value as JsonArray).isEmpty())
        assertEquals("tag", field.type)
    }

    @Test
    fun `unknown implementations resolve to null`() {
        assertEquals(null, SonarrAutoTaggingConditions.byImplementation("NopeSpecification"))
    }
}
