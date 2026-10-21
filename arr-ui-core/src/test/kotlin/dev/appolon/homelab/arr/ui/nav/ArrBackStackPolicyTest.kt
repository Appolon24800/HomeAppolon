package dev.appolon.homelab.arr.ui.nav

import dev.appolon.homelab.arr.core.ServerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrBackStackPolicyTest {

    @Test
    fun backAtTheStackRootExitsToTheDashboard() {
        assertEquals(ArrBackDirective.ExitToDashboard, arrBackDirective(canPop = false))
    }

    @Test
    fun backAnywhereElsePopsTheStack() {
        assertEquals(ArrBackDirective.PopStack, arrBackDirective(canPop = true))
    }

    @Test
    fun switchingToTheCurrentServerIsANoOp() {
        assertEquals(
            ServerSwitchDirective.None,
            serverSwitchDirective(current = ServerId("a"), target = ServerId("a")),
        )
    }

    @Test
    fun switchingServersPopsToTheRootAndSavesState() {
        val directive = serverSwitchDirective(current = ServerId("a"), target = ServerId("b"))
        assertTrue(directive is ServerSwitchDirective.SwitchTo)
        val switch = directive as ServerSwitchDirective.SwitchTo
        assertTrue(switch.popToRoot)
        assertTrue(switch.saveState)
        assertTrue(switch.restoreState)
    }

    @Test
    fun switchingWithNoCurrentServerStillSwitches() {
        assertEquals(
            ServerSwitchDirective.SwitchTo(popToRoot = true, saveState = true, restoreState = true),
            serverSwitchDirective(current = null, target = ServerId("b")),
        )
    }
}
