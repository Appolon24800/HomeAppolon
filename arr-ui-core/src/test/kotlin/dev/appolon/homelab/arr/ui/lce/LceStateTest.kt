package dev.appolon.homelab.arr.ui.lce

import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LceStateTest {

    @Test
    fun okResultBecomesContent() {
        val state = LceState.fromResult(ArrResult.ok(listOf(1, 2)))
        assertEquals(LceState.Content(listOf(1, 2)), state)
    }

    @Test
    fun okResultWithEmptyPayloadBecomesEmpty() {
        val state: LceState<List<Int>> =
            LceState.fromResult(ArrResult.ok(emptyList()), isEmpty = { it.isEmpty() })
        assertEquals(LceState.Empty, state)
    }

    @Test
    fun errResultBecomesErrorCarryingSameArrError() {
        val error = ArrError.Network(null)
        val state = LceState.fromResult(ArrResult.err(error))
        assertEquals(LceState.Error(error), state)
    }

    @Test
    fun everyArrErrorVariantMapsToADistinctStringResource() {
        val errors = listOf(
            ArrError.Network(null),
            ArrError.Http(500, null),
            ArrError.Auth(401),
            ArrError.Parse(RuntimeException("bad json")),
            ArrError.Offline(null),
            ArrError.Conflicted("action-1", 409),
            ArrError.Unknown(null),
        )
        val ids = errors.map(::arrErrorMessageId)
        assertEquals("each variant needs its own message", errors.size, ids.toSet().size)
        ids.forEach { assertTrue("resource id must be non-zero", it != 0) }
    }
}
