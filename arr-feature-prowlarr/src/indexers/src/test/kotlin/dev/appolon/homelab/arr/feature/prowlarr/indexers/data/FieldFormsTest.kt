package dev.appolon.homelab.arr.feature.prowlarr.indexers.data

import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCapabilityResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerPrivacy
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.prowlarr.model.SelectOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldFormsTest {

    private fun field(
        name: String,
        type: String? = "textbox",
        value: kotlinx.serialization.json.JsonElement? = null,
        privacy: PrivacyLevel = PrivacyLevel.NORMAL,
        selectOptions: List<SelectOption>? = null,
        hidden: String? = null,
    ) = Field(name = name, type = type, value = value, privacy = privacy, selectOptions = selectOptions, hidden = hidden)

    @Test
    fun `editableValues skips hidden fields and keeps values`() {
        val fields = listOf(
            field("baseUrl", hidden = "hidden", value = JsonPrimitive("https://x")),
            field("apiKey", value = JsonPrimitive("k")),
            field("offset", type = "number", value = JsonPrimitive(50)),
        )
        val values = FieldForms.editableValues(fields)
        assertEquals(setOf("apiKey", "offset"), values.keys)
        assertEquals("k", FieldForms.textOf(values["apiKey"]))
    }

    @Test
    fun `renderKind dispatches on type privacy and options`() {
        assertEquals(FieldRenderKind.CHECKBOX, FieldForms.renderKind(field("x", type = "checkbox")))
        assertEquals(
            FieldRenderKind.PASSWORD,
            FieldForms.renderKind(field("x", privacy = PrivacyLevel.PASSWORD)),
        )
        assertEquals(
            FieldRenderKind.PASSWORD,
            FieldForms.renderKind(field("x", privacy = PrivacyLevel.API_KEY)),
        )
        assertEquals(FieldRenderKind.NUMBER, FieldForms.renderKind(field("x", type = "number")))
        assertEquals(
            FieldRenderKind.SELECT,
            FieldForms.renderKind(
                field("x", selectOptions = listOf(SelectOption(value = 1, name = "One"))),
            ),
        )
        assertEquals(FieldRenderKind.TAG_SELECT, FieldForms.renderKind(field("x", type = "tagselect")))
        assertEquals(FieldRenderKind.TEXT, FieldForms.renderKind(field("x")))
    }

    @Test
    fun `applyValues writes back only edited names`() {
        val fields = listOf(
            field("a", value = JsonPrimitive("old")),
            field("b", value = JsonPrimitive("keep")),
        )
        val applied = FieldForms.applyValues(fields, mapOf("a" to JsonPrimitive("new")))
        assertEquals("new", applied[0].value?.let { FieldForms.textOf(it) })
        assertEquals("keep", applied[1].value?.let { FieldForms.textOf(it) })
    }

    @Test
    fun `idsOf reads arrays and single primitives`() {
        assertEquals(listOf(1, 2), FieldForms.idsOf(JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))))
        assertEquals(listOf(7), FieldForms.idsOf(JsonPrimitive(7)))
        assertEquals(emptyList<Int>(), FieldForms.idsOf(null))
    }

    @Test
    fun `boolOf and intOf tolerate missing values`() {
        assertFalse(FieldForms.boolOf(null))
        assertEquals(null, FieldForms.intOf(null))
        assertEquals(3, FieldForms.intOf(JsonPrimitive(3)))
    }

    @Test
    fun `seed criteria detection follows the field name prefix`() {
        assertTrue(FieldForms.isSeedCriteria(Field(name = "seedCriteria.seedRatio")))
        assertFalse(FieldForms.isSeedCriteria(Field(name = "seedRatio")))
    }
}

class IndexerCategoriesTest {

    private val defaults = listOf(
        IndexerCategory(
            id = 2000,
            name = "Movies",
            subCategories = listOf(
                IndexerCategory(id = 2010, name = "Foreign"),
                IndexerCategory(id = 2020, name = "Other"),
            ),
        ),
        IndexerCategory(id = 5000, name = "TV"),
    )

    @Test
    fun `selectedIds flattens capabilities`() {
        val resource = IndexerResource(
            capabilities = IndexerCapabilityResource(
                categories = listOf(
                    IndexerCategory(
                        id = 2000,
                        name = "Movies",
                        subCategories = listOf(IndexerCategory(id = 2010, name = "Foreign")),
                    ),
                ),
            ),
        )
        assertEquals(setOf(2000, 2010), IndexerCategories.selectedIds(resource))
    }

    @Test
    fun `withSelectedIds writes a flat list with resolved names`() {
        val resource = IndexerResource(privacy = IndexerPrivacy.PRIVATE)
        val written = IndexerCategories.withSelectedIds(resource, setOf(2010, 5000), defaults)
        val categories = written.capabilities.categories.orEmpty()
        assertEquals(2, categories.size)
        assertEquals(2010, categories[0].id)
        assertEquals("Foreign", categories[0].name)
        assertEquals(5000, categories[1].id)
        assertEquals("TV", categories[1].name)
    }

    @Test
    fun `flatten walks the whole tree parents first`() {
        val flat = IndexerCategories.flatten(defaults)
        assertEquals(listOf(2000, 2010, 2020, 5000), flat.map { it.id })
    }
}
