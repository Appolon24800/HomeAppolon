package dev.appolon.homelab

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.appolon.homelab.arr.sync.scheduler.ArrPollWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundSyncPersistenceTest {
    @Test
    fun pollingPassCompletesAndPersistsItsInterval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<ArrPollWorker>().build()
        manager.enqueue(request).result.get()
        try {
            val result = withTimeout(120_000L) {
                var info = manager.getWorkInfoById(request.id).get()
                while (info == null || !info.state.isFinished) {
                    delay(200)
                    info = manager.getWorkInfoById(request.id).get()
                }
                info
            }
            assertEquals(WorkInfo.State.SUCCEEDED, result.state)
        } finally {
            manager.cancelWorkById(request.id).result.get()
        }
    }
}
