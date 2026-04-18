package com.alexsiri7.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.alarm.AlarmScheduler
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import java.time.Instant
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
    private val mockTriggerRepository: TriggerRepository = mockk(relaxed = true)
    private val mockAlarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val mockGeofenceManager: GeofenceManager = mockk(relaxed = true)

    private lateinit var worker: BootReschedulerWorker

    @Before
    fun setup() {
        worker = BootReschedulerWorker(
            mockContext,
            mockWorkerParams,
            mockTriggerRepository,
            mockAlarmScheduler,
            mockGeofenceManager
        )
    }

    @Test
    fun `doWork succeeds when there are no triggers or geofences`() = runTest {
        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit
        coEvery { mockTriggerRepository.getAllScheduled() } returns emptyList()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { mockGeofenceManager.registerAllFromDb() }
    }

    @Test
    fun `doWork schedules only future triggers and skips past ones`() = runTest {
        val now = Instant.now()
        val futureTrigger = TriggerEntity(id = 1L, scheduledAt = now.plusSeconds(3600), status = TriggerStatus.SCHEDULED)
        val pastTrigger = TriggerEntity(id = 2L, scheduledAt = now.minusSeconds(3600), status = TriggerStatus.SCHEDULED)

        coEvery { mockGeofenceManager.registerAllFromDb() } returns Unit
        coEvery { mockTriggerRepository.getAllScheduled() } returns listOf(futureTrigger, pastTrigger)
        coEvery { mockAlarmScheduler.scheduleExactAlarm(any(), any()) } returns true

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { mockAlarmScheduler.scheduleExactAlarm(1L, futureTrigger.scheduledAt) }
        coVerify(exactly = 0) { mockAlarmScheduler.scheduleExactAlarm(2L, any()) }
    }
}
