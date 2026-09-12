package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import net.interstellarai.unreminder.service.activity.ActivityRecognitionManager
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BootReschedulerWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockGeofenceManager: GeofenceManager = mockk(relaxed = true)
    private val mockActivityRecognitionManager: ActivityRecognitionManager = mockk(relaxed = true)
    private val mockScheduler: EveningInvitationScheduler = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)

    private lateinit var worker: BootReschedulerWorker

    @Before
    fun setup() {
        worker = BootReschedulerWorker(
            mockContext,
            mockWorkerParams,
            mockGeofenceManager,
            mockActivityRecognitionManager,
            mockScheduler,
            widgetRefresher,
        )
    }

    @Test
    fun `doWork registers geofences and returns success`() = runTest {
        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { mockGeofenceManager.registerAllFromDb() }
    }

    @Test
    fun `doWork re-subscribes to activity transitions`() = runTest {
        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit

        worker.doWork()

        coVerify(exactly = 1) { mockActivityRecognitionManager.requestTransitionUpdates() }
    }

    @Test
    fun `doWork re-arms the evening invitation without disturbing a live schedule`() = runTest {
        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit

        worker.doWork()

        coVerify(exactly = 1) { mockScheduler.ensureScheduled() }
    }

    @Test
    fun `doWork refreshes the widget after a reboot`() = runTest {
        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit

        worker.doWork()

        verify(exactly = 1) { widgetRefresher.refresh() }
    }
}
