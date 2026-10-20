package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.core.ServerId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDedupeTest {

    private val serverA = ServerId("server-a")
    private val serverB = ServerId("server-b")

    private fun event(
        kind: NotificationKind = NotificationKind.GRAB,
        key: String = "k",
    ) = PollEvent(kind = kind, title = "t", body = "b", dedupeKey = key)

    @Test
    fun repeatedIdenticalEventsAreSuppressed() {
        val dedupe = NotificationDedupe()
        val first = dedupe.filter(serverA, listOf(event(key = "grab:1")))
        val second = dedupe.filter(serverA, listOf(event(key = "grab:1")))
        assertEquals(listOf(event(key = "grab:1")), first)
        assertEquals(emptyList<PollEvent>(), second)
    }

    @Test
    fun distinctKeysPassThroughInOrder() {
        val dedupe = NotificationDedupe()
        val events = listOf(event(key = "a"), event(key = "b"), event(key = "c"))
        assertEquals(events, dedupe.filter(serverA, events))
    }

    @Test
    fun keysAreScopedPerServer() {
        val dedupe = NotificationDedupe()
        dedupe.filter(serverA, listOf(event(key = "grab:1")))
        val onOtherServer = dedupe.filter(serverB, listOf(event(key = "grab:1")))
        assertEquals(listOf(event(key = "grab:1")), onOtherServer)
    }

    @Test
    fun keysAreScopedPerKind() {
        val dedupe = NotificationDedupe()
        dedupe.filter(serverA, listOf(event(kind = NotificationKind.GRAB, key = "x")))
        assertEquals(
            listOf(event(kind = NotificationKind.IMPORT, key = "x")),
            dedupe.filter(serverA, listOf(event(kind = NotificationKind.IMPORT, key = "x"))),
        )
    }

    @Test
    fun filterRecordsOnlyWhatItReturns() {
        val dedupe = NotificationDedupe()
        dedupe.filter(serverA, listOf(event(key = "seen")))
        // Mixed batch: the unseen half returns once, the seen half is dropped,
        // and a third pass over the same batch yields nothing new.
        val mixed = dedupe.filter(serverA, listOf(event(key = "seen"), event(key = "fresh")))
        assertEquals(listOf(event(key = "fresh")), mixed)
        assertEquals(emptyList<PollEvent>(), dedupe.filter(serverA, mixed))
    }

    @Test
    fun capacityEvictionMakesRoomForNewKeys() {
        val dedupe = NotificationDedupe(capacity = 2)
        dedupe.filter(serverA, listOf(event(key = "a"), event(key = "b")))
        assertEquals(listOf(event(key = "c")), dedupe.filter(serverA, listOf(event(key = "c"))))
        // "a" was evicted by the capacity trim, so it may notify again.
        assertEquals(listOf(event(key = "a")), dedupe.filter(serverA, listOf(event(key = "a"))))
    }

    @Test
    fun reseeingAKeyProtectsItFromEviction() {
        val dedupe = NotificationDedupe(capacity = 2)
        dedupe.filter(serverA, listOf(event(key = "a"), event(key = "b")))
        // Touching "a" again (even though suppressed) makes "b" the LRU victim.
        dedupe.filter(serverA, listOf(event(key = "a")))
        dedupe.filter(serverA, listOf(event(key = "c")))
        assertEquals(listOf(event(key = "b")), dedupe.filter(serverA, listOf(event(key = "b"))))
    }

    @Test
    fun clearResetsEverything() {
        val dedupe = NotificationDedupe()
        dedupe.filter(serverA, listOf(event(key = "a")))
        dedupe.clear()
        assertEquals(listOf(event(key = "a")), dedupe.filter(serverA, listOf(event(key = "a"))))
    }
}
