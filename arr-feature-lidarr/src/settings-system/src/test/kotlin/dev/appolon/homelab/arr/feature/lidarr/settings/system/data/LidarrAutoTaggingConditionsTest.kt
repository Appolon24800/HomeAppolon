package dev.appolon.homelab.arr.feature.lidarr.settings.system.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrAutoTaggingConditionsTest {

    @Test
    fun `every entry carries a distinct implementation and a display name`() {
        val implementations = LidarrAutoTaggingConditions.entries.map { it.implementation }
        assertEquals(implementations.size, implementations.distinct().size)
        LidarrAutoTaggingConditions.entries.forEach { entry ->
            assertTrue(entry.implementation!!.endsWith("Specification"))
            assertTrue(entry.implementationName!!.isNotBlank())
        }
    }

    @Test
    fun `status condition offers the artist status select options`() {
        val status = LidarrAutoTaggingConditions.byImplementation("StatusSpecification")!!
        val field = status.fields!!.single()
        val values = field.selectOptions!!.mapNotNull { it.name }
        assertEquals(listOf("Continuing", "Ended", "Deleted"), values)
        assertEquals(0, (field.value as JsonPrimitive).intOrNull)
    }

    @Test
    fun `quality and metadata profile conditions carry number fields`() {
        val quality = LidarrAutoTaggingConditions.byImplementation("QualityProfileSpecification")!!
        assertEquals("number", quality.fields!!.single().type)

        val metadata = LidarrAutoTaggingConditions.byImplementation("MetadataProfileSpecification")!!
        assertEquals("number", metadata.fields!!.single().type)
    }

    @Test
    fun `genre and tag conditions start with empty tag lists`() {
        val genre = LidarrAutoTaggingConditions.byImplementation("GenreSpecification")!!
        val tag = LidarrAutoTaggingConditions.byImplementation("TagSpecification")!!
        assertTrue((genre.fields!!.single().value as JsonArray).isEmpty())
        assertTrue((tag.fields!!.single().value as JsonArray).isEmpty())
        assertEquals("tag", genre.fields!!.single().type)
        assertEquals("tag", tag.fields!!.single().type)
    }

    @Test
    fun `monitored condition carries no fields`() {
        val monitored = LidarrAutoTaggingConditions.byImplementation("MonitoredSpecification")!!
        assertTrue(monitored.fields.isNullOrEmpty())
    }

    @Test
    fun `unknown implementations resolve to null`() {
        assertNull(LidarrAutoTaggingConditions.byImplementation("NopeSpecification"))
    }

    @Test
    fun `fromSchema parses a bare specification object`() {
        val schema = buildJsonObject {
            put("implementationName", "Root Folder")
            put("implementation", "RootFolderSpecification")
            put("name", "Root Folder")
        }
        val parsed = LidarrAutoTaggingConditions.fromSchema(schema)

        assertEquals(1, parsed.size)
        assertEquals("RootFolderSpecification", parsed.single().implementation)
    }

    @Test
    fun `fromSchema parses a specifications array and skips bad entries`() {
        val schema = buildJsonObject {
            put("specifications", kotlinx.serialization.json.buildJsonArray {
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("implementationName", "Genre")
                        put("implementation", "GenreSpecification")
                    },
                )
                add(kotlinx.serialization.json.JsonPrimitive("not-an-object"))
            })
        }
        val parsed = LidarrAutoTaggingConditions.fromSchema(schema)

        assertEquals(1, parsed.size)
        assertNotNull(parsed.single().implementation)
    }
}
