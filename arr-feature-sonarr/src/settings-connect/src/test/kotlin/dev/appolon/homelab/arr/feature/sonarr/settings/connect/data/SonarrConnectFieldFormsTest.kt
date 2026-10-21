package dev.appolon.homelab.arr.feature.sonarr.settings.connect.data

import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.sonarr.model.SelectOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrConnectFieldFormsTest {

    private fun field(
        name: String? = "apiKey",
        type: String? = null,
        privacy: PrivacyLevel = PrivacyLevel.NORMAL,
        hidden: String? = null,
        advanced: Boolean = false,
        section: String? = null,
        selectOptions: List<SelectOption>? = null,
        value: kotlinx.serialization.json.JsonElement = JsonNull,
    ) = Field(
        name = name,
        type = type,
        privacy = privacy,
        hidden = hidden,
        advanced = advanced,
        section = section,
        selectOptions = selectOptions,
        value = value,
    )

    @Test
    fun `hidden fields are not editable`() {
        assertFalse(SonarrConnectFieldForms.isEditable(field(hidden = "hidden")))
        assertFalse(SonarrConnectFieldForms.isEditable(field(hidden = "hiddenIfNotEnabled")))
        assertTrue(SonarrConnectFieldForms.isEditable(field(hidden = null)))
    }

    @Test
    fun `render kind dispatches on type privacy and options`() {
        assertEquals(
            ConnectFieldRenderKind.CHECKBOX,
            SonarrConnectFieldForms.renderKind(field(type = "checkbox")),
        )
        assertEquals(
            ConnectFieldRenderKind.PASSWORD,
            SonarrConnectFieldForms.renderKind(field(privacy = PrivacyLevel.PASSWORD)),
        )
        assertEquals(
            ConnectFieldRenderKind.PASSWORD,
            SonarrConnectFieldForms.renderKind(field(privacy = PrivacyLevel.APIKEY)),
        )
        assertEquals(
            ConnectFieldRenderKind.NUMBER,
            SonarrConnectFieldForms.renderKind(field(type = "number")),
        )
        assertEquals(
            ConnectFieldRenderKind.SELECT,
            SonarrConnectFieldForms.renderKind(
                field(selectOptions = listOf(SelectOption(value = 1, name = "One"))),
            ),
        )
        assertEquals(
            ConnectFieldRenderKind.TAG_SELECT,
            SonarrConnectFieldForms.renderKind(field(name = "categories")),
        )
        assertEquals(
            ConnectFieldRenderKind.TEXT,
            SonarrConnectFieldForms.renderKind(field(type = "text")),
        )
    }

    @Test
    fun `seed criteria fields are recognized`() {
        assertTrue(SonarrConnectFieldForms.isSeedCriteria(field(name = "seedCriteria.seedRatio")))
        assertFalse(SonarrConnectFieldForms.isSeedCriteria(field(name = "baseUrl")))
    }

    @Test
    fun `seed settings and categories read out of field values`() {
        val fields = listOf(
            field(
                name = "seedCriteria.seedRatio",
                value = JsonPrimitive(1.5),
            ),
            field(name = "seedCriteria.seedTime", value = JsonPrimitive(120.0)),
            field(
                name = "categories",
                value = JsonArray(listOf(JsonPrimitive(5000), JsonPrimitive(5030))),
            ),
        )
        assertEquals(1.5, SonarrConnectFieldForms.seedRatio(fields)!!, 0.001)
        assertEquals(120.0, SonarrConnectFieldForms.seedTime(fields)!!, 0.001)
        assertEquals(listOf(5000, 5030), SonarrConnectFieldForms.categoryIds(fields))
    }

    @Test
    fun `editable values skip hidden fields and applyValues round-trips`() {
        val visible = field(name = "baseUrl", value = JsonPrimitive("https://x"))
        val hidden = field(name = "secret", hidden = "hidden")
        val fields = listOf(visible, hidden)

        val values = SonarrConnectFieldForms.editableValues(fields)
        assertEquals(setOf("baseUrl"), values.keys)

        val applied = SonarrConnectFieldForms.applyValues(
            fields,
            values + ("baseUrl" to JsonPrimitive("https://y")),
        )
        assertEquals("https://y", (applied.first().value as JsonPrimitive).content)
        // The hidden field keeps its descriptor untouched.
        assertEquals(hidden, applied[1])
    }

    @Test
    fun `idsOf tolerates arrays and bare primitives`() {
        assertEquals(
            listOf(2, 3),
            SonarrConnectFieldForms.idsOf(JsonArray(listOf(JsonPrimitive(2), JsonPrimitive(3)))),
        )
        assertEquals(listOf(7), SonarrConnectFieldForms.idsOf(JsonPrimitive(7)))
        assertEquals(emptyList<Int>(), SonarrConnectFieldForms.idsOf(JsonNull))
    }
}
