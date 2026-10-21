package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPlannerTest {

    private val server = ArrServer(
        id = ServerId("radarr-1"),
        type = AppType.RADARR,
        name = "Radarr",
        baseUrl = "http://radarr.example",
        apiKeyEncrypted = "cipher",
    )

    private val planner = SummaryPlanner(
        title = { kind -> "title-${kind.name.lowercase()}" },
        body = { kind, _, _ -> "body-${kind.name.lowercase()}" },
    )

    private fun resultOf(
        grabs: Int = 0,
        imports: Int = 0,
        failures: Int = 0,
        health: Int = 0,
        disk: Int = 0,
        events: List<PollEvent> = emptyList(),
    ) = PollResult(
        newGrabs = grabs,
        newImports = imports,
        failures = failures,
        healthIssues = health,
        diskWarnings = disk,
        events = events,
    )

    @Test
    fun zeroCountsProduceNothing() {
        assertEquals(emptyList<PollEvent>(), planner.plan(server, resultOf()))
    }

    @Test
    fun everyCountProducesItsKindSummary() {
        val planned = planner.plan(
            server,
            resultOf(grabs = 1, imports = 2, failures = 3, health = 4, disk = 5),
        )
        assertEquals(NotificationKind.entries.toList(), planned.map { it.kind })
    }

    @Test
    fun summariesCarryResolvedTextAndServer() {
        val planned = planner.plan(server, resultOf(grabs = 2))
        val grab = planned.single()
        assertEquals("title-grab", grab.title)
        assertEquals("body-grab", grab.body)
    }

    @Test
    fun summaryKeysAreStablePerKindAndCount() {
        val first = planner.plan(server, resultOf(imports = 2)).single()
        val second = planner.plan(server, resultOf(imports = 2)).single()
        assertEquals("summary:IMPORT:2", first.dedupeKey)
        assertEquals(first.dedupeKey, second.dedupeKey)

        val changed = planner.plan(server, resultOf(imports = 3)).single()
        assertEquals("summary:IMPORT:3", changed.dedupeKey)
    }

    @Test
    fun granularEventsSuppressOnlyTheirKindsSummary() {
        val granular = PollEvent(
            kind = NotificationKind.GRAB,
            title = "Movie grabbed",
            body = "Some Movie queued.",
            dedupeKey = "grab:movie:1",
        )
        val planned = planner.plan(
            server,
            resultOf(grabs = 3, imports = 1, events = listOf(granular)),
        )
        assertEquals(listOf(NotificationKind.IMPORT), planned.map { it.kind })
    }

    @Test
    fun negativeCountsProduceNothing() {
        assertEquals(emptyList<PollEvent>(), planner.plan(server, resultOf(grabs = -1, disk = -2)))
    }
}
