package net.interstellarai.unreminder.service.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillSchedulerTest {

    private val workManager: WorkManager = mockk(relaxed = true)
    private val scheduler = RefillScheduler(workManager)

    @Test
    fun `enqueueForHabit replaces the habit's unique work with an immediate request`() {
        val request = slot<OneTimeWorkRequest>()

        scheduler.enqueueForHabit(7L)

        verify(exactly = 1) { workManager.enqueueUniqueWork("refill-7", ExistingWorkPolicy.REPLACE, capture(request)) }
        assertEquals(0L, request.captured.workSpec.initialDelay)
        assertEquals(7L, request.captured.workSpec.input.getLong(RefillWorker.KEY_HABIT_ID, -1L))
        assertFalse(request.captured.workSpec.input.getBoolean(RefillWorker.KEY_REPLACE, true))
    }

    @Test
    fun `enqueueRegenerate uses its own unique name with REPLACE, sets the replace flag, and returns the request id`() {
        val request = slot<OneTimeWorkRequest>()

        val id = scheduler.enqueueRegenerate(7L)

        verify(exactly = 1) { workManager.enqueueUniqueWork("regenerate-7", ExistingWorkPolicy.REPLACE, capture(request)) }
        assertEquals(request.captured.id, id)
        assertEquals(0L, request.captured.workSpec.initialDelay)
        assertEquals(7L, request.captured.workSpec.input.getLong(RefillWorker.KEY_HABIT_ID, -1L))
        assertTrue(request.captured.workSpec.input.getBoolean(RefillWorker.KEY_REPLACE, false))
    }

    @Test
    fun `enqueuePaced staggers one KEEP request per habit a minute apart`() {
        val names = mutableListOf<String>()
        val requests = mutableListOf<OneTimeWorkRequest>()

        scheduler.enqueuePaced(listOf(1L, 2L, 3L))

        verify(exactly = 3) { workManager.enqueueUniqueWork(capture(names), ExistingWorkPolicy.KEEP, capture(requests)) }
        assertEquals(listOf("refill-1", "refill-2", "refill-3"), names)
        assertEquals(listOf(0L, 60_000L, 120_000L), requests.map { it.workSpec.initialDelay })
        assertEquals(listOf(1L, 2L, 3L), requests.map { it.workSpec.input.getLong(RefillWorker.KEY_HABIT_ID, -1L) })
        assertEquals(listOf(false, false, false), requests.map { it.workSpec.input.getBoolean(RefillWorker.KEY_REPLACE, true) })
    }

    @Test
    fun `enqueuePaced with no habits enqueues nothing`() {
        scheduler.enqueuePaced(emptyList())

        verify(exactly = 0) { workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }
}
