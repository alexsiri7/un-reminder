package net.interstellarai.unreminder.ui.recent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.worker.RandomIntervalWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class TriggerWithHabit(
    val trigger: TriggerEntity,
    val habitName: String?
)

@HiltViewModel
class RecentTriggersViewModel @Inject constructor(
    triggerRepository: TriggerRepository,
    habitRepository: HabitRepository,
    workManager: WorkManager,
) : ViewModel() {

    val triggers: StateFlow<List<TriggerWithHabit>> = combine(
        triggerRepository.getRecentTriggers(20),
        habitRepository.getAll()
    ) { triggers, habits ->
        val habitMap = habits.associateBy { it.id }
        triggers.map { trigger ->
            TriggerWithHabit(
                trigger = trigger,
                habitName = trigger.habitId?.let { habitMap[it]?.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextTrigger: StateFlow<NextTriggerState> =
        workManager.getWorkInfosForUniqueWorkFlow(RandomIntervalWorker.WORK_NAME)
            .map { infos ->
                val enqueued = infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                    ?: return@map NextTriggerState.NotScheduled
                val millis = enqueued.nextScheduleTimeMillis
                if (millis == Long.MAX_VALUE) NextTriggerState.NotScheduled
                else NextTriggerState.Scheduled(Instant.ofEpochMilli(millis))
            }
            .distinctUntilChanged()
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "WorkManager nextTrigger flow error — defaulting to NotScheduled", e)
                emit(NextTriggerState.NotScheduled)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                NextTriggerState.NotScheduled,
            )

    private companion object {
        private const val TAG = "RecentTriggersVM"
    }
}
