package dev.appolon.homelab.arr.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionViewModelsTest {
    private class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test
    fun screenStateSurvivesNavigationUntilTheConnectionChanges() {
        val holder = ConnectionViewModels()
        val original = holder.ownerFor(1)
        val probe = Probe()
        original.viewModelStore.put("screen", probe)

        assertSame(original, holder.ownerFor(1))
        assertFalse(probe.cleared)
        val replacement = holder.ownerFor(2)
        assertNotSame(original, replacement)
        assertTrue(probe.cleared)
        assertEquals(emptySet<String>(), replacement.viewModelStore.keys())
    }

    @Test
    fun removingTheNavigationEntryClearsItsScreenViewModels() {
        val navigationStore = ViewModelStore()
        val holder = ConnectionViewModels()
        navigationStore.put("connection", holder)
        val probe = Probe()
        holder.ownerFor(1).viewModelStore.put("screen", probe)
        navigationStore.clear()
        assertTrue(probe.cleared)
    }
}
