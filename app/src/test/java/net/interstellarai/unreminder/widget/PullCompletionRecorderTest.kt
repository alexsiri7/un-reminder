package net.interstellarai.unreminder.widget

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PullCompletionRecorderTest {

    private val triggerRepository: TriggerRepository = mockk()
    private val variationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val dismissalTracker: DismissalTracker = mockk(relaxUnitFun = true)
    private val recorder = PullCompletionRecorder(triggerRepository, variationRepository, dismissalTracker)

    @Test
    fun `inserts a COMPLETED trigger from the widget, runs promotion, then consumes the shown variant`() = runTest {
        val inserted = slot<TriggerEntity>()
        coEvery { triggerRepository.insert(capture(inserted)) } returns 99L

        recorder.complete(5L, 50L, PullCompletionRecorder.SOURCE_WIDGET)

        assertEquals(5L, inserted.captured.habitId)
        assertEquals(TriggerStatus.COMPLETED, inserted.captured.status)
        assertEquals("widget", inserted.captured.source)
        assertEquals(inserted.captured.scheduledAt, inserted.captured.firedAt)
        coVerifyOrder {
            triggerRepository.insert(any())
            dismissalTracker.onCompleted(99L)
            variationRepository.markConsumed(50L)
        }
        coVerify(exactly = 1) { variationRepository.markConsumed(any()) }
    }

    @Test
    fun `a fallback completion consumes nothing`() = runTest {
        coEvery { triggerRepository.insert(any()) } returns 99L

        recorder.complete(5L, null, PullCompletionRecorder.SOURCE_WIDGET)

        coVerify(exactly = 0) { variationRepository.markConsumed(any()) }
        coVerify(exactly = 1) { dismissalTracker.onCompleted(99L) }
    }

    @Test
    fun `records the source it is given`() = runTest {
        val inserted = slot<TriggerEntity>()
        coEvery { triggerRepository.insert(capture(inserted)) } returns 99L

        recorder.complete(5L, 50L, "detail")

        assertEquals("detail", inserted.captured.source)
    }

    @Test
    fun `a failed trigger insert escapes and consumes nothing`() = runTest {
        coEvery { triggerRepository.insert(any()) } throws IllegalStateException("disk full")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { recorder.complete(5L, 50L, PullCompletionRecorder.SOURCE_WIDGET) }
        }

        coVerify(exactly = 0) { dismissalTracker.onCompleted(any()) }
        coVerify(exactly = 0) { variationRepository.markConsumed(any()) }
    }

    @Test
    fun `a failure after the trigger is written does not escape as if nothing were written`() = runTest {
        coEvery { triggerRepository.insert(any()) } returns 99L
        coEvery { dismissalTracker.onCompleted(99L) } throws IllegalStateException("disk full")

        recorder.complete(5L, 50L, PullCompletionRecorder.SOURCE_WIDGET)

        coVerify(exactly = 1) { triggerRepository.insert(any()) }
    }
}
