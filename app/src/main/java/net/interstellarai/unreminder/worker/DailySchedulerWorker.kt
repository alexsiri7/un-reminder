package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.alarm.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

@HiltWorker
class DailySchedulerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val windowRepository: WindowRepository,
    private val triggerRepository: TriggerRepository,
    private val alarmScheduler: AlarmScheduler
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_scheduler"
    }

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val dayBit = todayBit(today.dayOfWeek)

        val activeWindows = windowRepository.getActiveWindows()

        for (window in activeWindows) {
            if (window.daysOfWeekBitmask and dayBit == 0) continue

            val startSeconds = window.startTime.toSecondOfDay().toLong()
            val endSeconds = window.endTime.toSecondOfDay().toLong()
            if (endSeconds <= startSeconds) continue

            val times = (1..window.frequencyPerDay).map {
                val randomSecond = Random.nextLong(startSeconds, endSeconds)
                LocalTime.ofSecondOfDay(randomSecond)
            }.sorted()

            for (time in times) {
                val scheduledInstant = LocalDateTime.of(today, time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()

                if (scheduledInstant.isBefore(Instant.now())) continue

                val triggerId = triggerRepository.insert(
                    TriggerEntity(
                        windowId = window.id,
                        scheduledAt = scheduledInstant,
                        status = TriggerStatus.SCHEDULED
                    )
                )

                alarmScheduler.scheduleExactAlarm(triggerId, scheduledInstant)
            }
        }

        return Result.success()
    }

    private fun todayBit(dayOfWeek: DayOfWeek): Int {
        // bit 0 = Monday, bit 6 = Sunday
        return 1 shl (dayOfWeek.value - 1)
    }
}
