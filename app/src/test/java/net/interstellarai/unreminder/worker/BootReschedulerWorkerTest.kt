package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BootReschedulerWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockGeofenceManager: GeofenceManager = mockk(relaxed = true)

    private lateinit var worker: BootReschedulerWorker

    @Before
    fun setup() {
        worker = BootReschedulerWorker(
            mockContext,
            mockWorkerParams,
            mockGeofenceManager
        )
    }

    @Test
    fun `doWork registers geofences and returns success`() = runTest {
        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { mockGeofenceManager.registerAllFromDb() }
    }
}
