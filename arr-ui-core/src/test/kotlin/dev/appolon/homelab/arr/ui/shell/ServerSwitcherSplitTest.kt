package dev.appolon.homelab.arr.ui.shell

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSwitcherSplitTest {

    private fun server(id: String) = ShellServer(ServerId(id), id, AppType.RADARR)

    @Test
    fun emptyServerListSplitsIntoTwoEmptyHalves() {
        val (visible, overflow) = splitServers(emptyList(), maxVisible = 4)
        assertTrue(visible.isEmpty())
        assertTrue(overflow.isEmpty())
    }

    @Test
    fun fewServersAreAllVisibleWithNoOverflow() {
        val servers = (1..3).map { server("srv-$it") }
        val (visible, overflow) = splitServers(servers, maxVisible = 4)
        assertEquals(servers, visible)
        assertTrue(overflow.isEmpty())
    }

    @Test
    fun extraServersMoveToTheOverflowMenuInOrder() {
        val servers = (1..6).map { server("srv-$it") }
        val (visible, overflow) = splitServers(servers, maxVisible = 4)
        assertEquals(servers.take(4), visible)
        assertEquals(servers.drop(4), overflow)
    }

    @Test
    fun zeroVisibleChipsPutsEverythingInTheOverflowMenu() {
        val servers = listOf(server("a"), server("b"))
        val (visible, overflow) = splitServers(servers, maxVisible = 0)
        assertTrue(visible.isEmpty())
        assertEquals(servers, overflow)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeVisibleChipBudgetIsRejected() {
        splitServers(emptyList(), maxVisible = -1)
    }
}
