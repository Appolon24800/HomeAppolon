package dev.appolon.homelab.arr.feature.radarr.settings.system.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTextTest {

    @Test
    fun `dotNetDurationSeconds parses time spans with and without fractions`() {
        assertEquals(1L, SystemText.dotNetDurationSeconds("00:00:01.7431281"))
        assertEquals(3661L, SystemText.dotNetDurationSeconds("01:01:01"))
        assertEquals(60L, SystemText.dotNetDurationSeconds("00:01:00"))
        assertEquals(45296L, SystemText.dotNetDurationSeconds("12:34:56"))
    }

    @Test
    fun `dotNetDurationSeconds tolerates two-part spans and rejects junk`() {
        assertEquals(61L, SystemText.dotNetDurationSeconds("01:01"))
        assertNull(SystemText.dotNetDurationSeconds(null))
        assertNull(SystemText.dotNetDurationSeconds(""))
        assertNull(SystemText.dotNetDurationSeconds("not-a-span"))
        assertNull(SystemText.dotNetDurationSeconds("00:99:00"))
    }

    @Test
    fun `intervalMinutes humanizes cadence like the Radarr UI`() {
        assertEquals("1d", SystemText.intervalMinutes(1440))
        assertEquals("2d", SystemText.intervalMinutes(2880))
        assertEquals("6h", SystemText.intervalMinutes(360))
        assertEquals("90m", SystemText.intervalMinutes(90))
        assertEquals("0m", SystemText.intervalMinutes(0))
    }
}
