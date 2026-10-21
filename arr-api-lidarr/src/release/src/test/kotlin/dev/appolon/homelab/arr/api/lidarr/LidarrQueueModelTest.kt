package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.CommandResource
import dev.appolon.homelab.arr.api.lidarr.model.CommandStatus
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrQueueModelTest {
    @Test
    fun schemaFieldsRetainDatesLongSizesAndNullableMapValues() {
        val queue = queueDto<QueueResourcePagingResource>("queue/getQueue").records!!.single()
        assertEquals(512.25, queue.sizeleft, 0.0)
        assertEquals("00:01:00", queue.timeleft)
        assertEquals("2026-01-01T00:01:00Z", queue.estimatedCompletionTime)
        assertEquals(TrackedDownloadState.IMPORT_PENDING, queue.trackedDownloadState)
        val release = queueDto<ReleaseResource>("release/release")
        assertEquals(4294967296L, release.size)
        assertEquals("2026-01-01T00:00:00Z", release.publishDate)
        assertNull(release.leechers)
        val history = queueDto<HistoryResourcePagingResource>("history/getHistory")
            .records!!.single()
        assertEquals(EntityHistoryEventType.TRACK_FILE_IMPORTED, history.eventType)
        assertTrue(history.data!!.containsKey("nullableValue"))
        assertNull(history.data["nullableValue"])
    }

    @Test
    fun unknownEnumsCoerceAndUnenumeratedStringsRemainUntouched() {
        val resource = ArrJson.decodeFromString<QueueResource>(
            """{"protocol":"future","trackedDownloadState":"future",
                "status":"new-server-state","serverExtension":true}""",
        )
        assertEquals(DownloadProtocol.UNKNOWN, resource.protocol)
        assertEquals(TrackedDownloadState.DOWNLOADING, resource.trackedDownloadState)
        assertEquals("new-server-state", resource.status)
        assertEquals(CommandStatus.QUEUED, ArrJson.decodeFromString<CommandResource>(
            """{"status":"future"}""",
        ).status)
    }

    @Test
    fun enumsWriteSpecWireNames() {
        val encoded = ArrJson.parseToJsonElement(ArrJson.encodeToString(
            QueueResource(trackedDownloadState = TrackedDownloadState.DOWNLOAD_FAILED_PENDING),
        )).jsonObject
        assertEquals("downloadFailedPending", encoded.getValue("trackedDownloadState")
            .jsonPrimitive.content)
        assertEquals("trackFileImported", EntityHistoryEventType.TRACK_FILE_IMPORTED.toString())
    }
}
