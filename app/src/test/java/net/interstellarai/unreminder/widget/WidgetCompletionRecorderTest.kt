package net.interstellarai.unreminder.widget

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCompletionRecorderTest {

    private val triggerRepository: TriggerRepository = mockk()
    private val dismissalTracker: DismissalTracker = mockk(relaxUnitFun = true)
    private val recorder = WidgetCompletionRecorder(triggerRepository, dismissalTracker)

    @Test
    fun `inserts a COMPLETED trigger from the widget and then runs promotion on it`() = runTest {
        val inserted = slot<TriggerEntity>()
        coEvery { triggerRepository.insert(capture(inserted)) } returns 99L

        recorder.complete(5L)

        assertEquals(5L, inserted.captured.habitId)
        assertEquals(TriggerStatus.COMPLETED, inserted.captured.status)
        assertEquals("widget", inserted.captured.source)
        assertEquals(inserted.captured.scheduledAt, inserted.captured.firedAt)
        coVerifyOrder {
            triggerRepository.insert(any())
            dismissalTracker.onCompleted(99L)
        }
    }
}
