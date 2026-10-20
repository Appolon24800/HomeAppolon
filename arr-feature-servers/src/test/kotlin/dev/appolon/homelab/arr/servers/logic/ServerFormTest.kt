package dev.appolon.homelab.arr.servers.logic

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.servers.logic.ServerForm.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFormTest {

    @Test
    fun `complete form has no problems and normalizes its url`() {
        val form = ServerForm(
            name = "Main Radarr",
            type = AppType.RADARR,
            baseUrl = "host:7878/radarr",
            apiKey = "0123456789abcdef0123456789abcdef",
        )
        assertTrue(form.problems.isEmpty())
        assertTrue(form.isValid)
        assertEquals("http://host:7878/radarr/", form.toBaseUrl())
    }

    @Test
    fun `blank fields are each their own problem`() {
        val problems = ServerForm().problems
        assertTrue(Problem.NAME_BLANK in problems)
        assertTrue(Problem.URL_BLANK in problems)
        assertTrue(Problem.API_KEY_BLANK in problems)
        assertFalse(Problem.BASIC_AUTH_INCOMPLETE in problems)
    }

    @Test
    fun `hostless urls are reported as invalid not blank`() {
        val problems = ServerForm(
            name = "n",
            baseUrl = "http://",
            apiKey = "k",
        ).problems
        assertEquals(setOf<Problem>(Problem.URL_INVALID), problems)
    }

    @Test
    fun `urls with only a scheme prefixing quirk still need a host`() {
        // normalizeBaseUrl forces http onto schemeless input, so "ftp://host"
        // becomes "http://ftp//host" and parses; only a truly hostless URL
        // stays invalid.
        val problems = ServerForm(
            name = "n",
            baseUrl = "http://:7878",
            apiKey = "k",
        ).problems
        assertEquals(setOf<Problem>(Problem.URL_INVALID), problems)
    }

    @Test
    fun `spaces in the host do not parse`() {
        val problems = ServerForm(
            name = "n",
            baseUrl = "not a url",
            apiKey = "k",
        ).problems
        assertEquals(setOf<Problem>(Problem.URL_INVALID), problems)
    }

    @Test
    fun `basic auth requires both fields`() {
        val half = ServerForm(
            name = "n",
            baseUrl = "http://host",
            apiKey = "k",
            basicAuthUser = "user",
        )
        assertTrue(Problem.BASIC_AUTH_INCOMPLETE in half.problems)

        val whole = half.copy(basicAuthPassword = "pass")
        assertTrue(whole.problems.isEmpty())
    }

    @Test
    fun `preview is hidden while blank and normalized otherwise`() {
        assertNull(ServerForm().previewUrl())
        assertEquals(
            "https://host:30025/sonarr/",
            ServerForm(baseUrl = "https://host:30025/sonarr").previewUrl(),
        )
    }

    @Test
    fun `https urls are not downgraded`() {
        val form = ServerForm(name = "n", baseUrl = "https://host", apiKey = "k")
        assertEquals("https://host/", form.toBaseUrl())
    }
}
