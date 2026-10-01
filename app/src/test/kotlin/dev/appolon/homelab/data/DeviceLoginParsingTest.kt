package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLoginParsingTest {

    @Test
    fun `live shape decodes`() {
        val body = """
            {"id":"PVHYA45H","userCode":"PVHYA45H","verificationUri":"https://auth.appolon.dev/device",
             "verificationURIComplete":"https://auth.appolon.dev/device?user_code=PVHYA45H",
             "expiresAt":"2026-09-26T13:39:23Z","interval":3}
        """.trimIndent()
        val req = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<PocketId.DeviceLoginRequest>(body)
        assertEquals("PVHYA45H", req.userCode)
        assertEquals(3, req.interval)
        assertEquals("https://auth.appolon.dev/device?user_code=PVHYA45H", req.verificationUriComplete)
    }

    @Test
    fun `deployed spelling of verificationUriComplete also decodes`() {
        val body = """
            {"id":"A","userCode":"A","verificationUri":"https://x/device",
             "verificationUriComplete":"https://x/device?user_code=A",
             "expiresAt":"2026-09-26T13:39:23Z","interval":3}
        """.trimIndent()
        val req = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<PocketId.DeviceLoginRequest>(body)
        assertEquals("https://x/device?user_code=A", req.verificationUriComplete)
    }
}
