package dev.appolon.homelab.arr.feature.lidarr.settings.connect.data

import dev.appolon.homelab.arr.api.lidarr.model.Field
import dev.appolon.homelab.arr.api.lidarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.lidarr.model.SelectOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrConnectFieldFormsTest {

    private fun field(
        name: String? = "apiKey",
        type: String? = null,
        privacy: PrivacyLevel = PrivacyLevel.NORMAL,
        hidden: String? = null,
        advanced: Boolean = false,
        section: String? = null,
        selectOptions: List<SelectOption>? = null,
        value: JsonElement = JsonNull,
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
        assertFalse(LidarrConnectFieldForms.isEditable(field(hidden = "hidden")))
        assertFalse(LidarrConnectFieldForms.isEditable(field(hidden = "hiddenIfNotEnabled")))
        assertTrue(LidarrConnectFieldForms.isEditable(field(hidden = null)))
    }

    @Test
    fun `render kind dispatches on type privacy and options`() {
        assertEquals(
            ConnectFieldRenderKind.CHECKBOX,
            LidarrConnectFieldForms.renderKind(field(type = "checkbox")),
        )
        assertEquals(
            ConnectFieldRenderKind.PASSWORD,
            LidarrConnectFieldForms.renderKind(field(privacy = PrivacyLevel.PASSWORD)),
        )
        assertEquals(
            ConnectFieldRenderKind.PASSWORD,
            LidarrConnectFieldForms.renderKind(field(privacy = PrivacyLevel.APIKEY)),
        )
        assertEquals(
            ConnectFieldRenderKind.NUMBER,
            LidarrConnectFieldForms.renderKind(field(type = "number")),
        )
        assertEquals(
            ConnectFieldRenderKind.SELECT,
            LidarrConnectFieldForms.renderKind(
                field(selectOptions = listOf(SelectOption(value = 1, name = "One"))),
            ),
        )
        assertEquals(
            ConnectFieldRenderKind.TAG_SELECT,
            LidarrConnectFieldForms.renderKind(field(type = "tag")),
        )
        assertEquals(
            ConnectFieldRenderKind.TEXT,
            LidarrConnectFieldForms.renderKind(field(type = "text")),
        )
    }

    @Test
    fun `editable values skip hidden fields and applyValues round-trips`() {
        val visible = field(name = "baseUrl", value = JsonPrimitive("https://x"))
        val hidden = field(name = "secret", hidden = "hidden")
        val fields = listOf(visible, hidden)

        val values = LidarrConnectFieldForms.editableValues(fields)
        assertEquals(setOf("baseUrl"), values.keys)

        val applied = LidarrConnectFieldForms.applyValues(
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
            LidarrConnectFieldForms.idsOf(JsonArray(listOf(JsonPrimitive(2), JsonPrimitive(3)))),
        )
        assertEquals(listOf(7), LidarrConnectFieldForms.idsOf(JsonPrimitive(7)))
        assertEquals(emptyList<Int>(), LidarrConnectFieldForms.idsOf(JsonNull))
    }
}
