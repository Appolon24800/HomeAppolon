package dev.appolon.homelab.arr.feature.lidarr.settings.system.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTextTest {

    @Test
    fun `dotNetDurationSeconds parses hours minutes seconds and truncates fractions`() {
        assertEquals(2L * 3600 + 3 * 60 + 4, SystemText.dotNetDurationSeconds("02:03:04.1234567"))
        assertEquals(65L, SystemText.dotNetDurationSeconds("00:01:05"))
        assertEquals(5L, SystemText.dotNetDurationSeconds("00:00:05"))
    }

    @Test
    fun `dotNetDurationSeconds rejects null blank and malformed values`() {
        assertNull(SystemText.dotNetDurationSeconds(null))
        assertNull(SystemText.dotNetDurationSeconds("   "))
        assertNull(SystemText.dotNetDurationSeconds("not-a-span"))
        assertNull(SystemText.dotNetDurationSeconds("00:99:00"))
        assertNull(SystemText.dotNetDurationSeconds("1:2:3:4"))
    }

    @Test
    fun `intervalMinutes humanizes days hours and minutes`() {
        assertEquals("0m", SystemText.intervalMinutes(0))
        assertEquals("1d", SystemText.intervalMinutes(1440))
        assertEquals("12h", SystemText.intervalMinutes(720))
        assertEquals("90m", SystemText.intervalMinutes(90))
    }
}
