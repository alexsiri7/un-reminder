package net.interstellarai.unreminder.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.WindowRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class WidgetRefresherTest {

    private val context: Context = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val picker: DoableHabitPicker = mockk()
    private val windowRepository: WindowRepository = mockk()
    private val refresher = WidgetRefresher(context, workManager, picker, windowRepository)

    @Test
    fun `refresh enqueues one replaceable refresh job`() {
        refresher.refresh()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                WidgetRefresher.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `arms a single wake-up at the next window boundary`() = runTest {
        coEvery { windowRepository.getActiveWindows() } returns listOf(
            WindowEntity(startTime = LocalTime.of(14, 0), endTime = LocalTime.of(16, 0), daysOfWeekBitmask = 0b1111111)
        )
        val request = slot<OneTimeWorkRequest>()

        refresher.scheduleRefreshAtNextWindowBoundary(now = LocalDateTime.of(2026, 9, 11, 12, 0))

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                WidgetRefresher.WINDOW_BOUNDARY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                capture(request),
            )
        }
        assertEquals(TimeUnit.HOURS.toMillis(2), request.captured.workSpec.initialDelay)
    }

    @Test
    fun `drops the wake-up when no window will ever open or close`() = runTest {
        coEvery { windowRepository.getActiveWindows() } returns emptyList()

        refresher.scheduleRefreshAtNextWindowBoundary(now = LocalDateTime.of(2026, 9, 11, 12, 0))

        verify(exactly = 1) { workManager.cancelUniqueWork(WidgetRefresher.WINDOW_BOUNDARY_WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }
}
