package dev.appolon.homelab.arr.ui.nav

import dev.appolon.homelab.arr.core.ServerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun switchingServersDoesNotRestoreAnotherServersArguments() {
        val directive = serverSwitchDirective(current = ServerId("a"), target = ServerId("b"))
        assertTrue(directive is ServerSwitchDirective.SwitchTo)
        val switch = directive as ServerSwitchDirective.SwitchTo
        assertTrue(switch.popToRoot)
        assertFalse(switch.saveState)
        assertFalse(switch.restoreState)
    }

    @Test
    fun switchingWithNoCurrentServerStillSwitches() {
        assertEquals(
            ServerSwitchDirective.SwitchTo(popToRoot = true, saveState = false, restoreState = false),
            serverSwitchDirective(current = null, target = ServerId("b")),
        )
    }
}
