package net.interstellarai.unreminder.ui.now

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import java.time.Instant
import javax.inject.Inject

data class NowMenuItem(
    val habitId: Long,
    val name: String,
    val description: String?,
)

sealed interface NowMenuUiState {
    data object Loading : NowMenuUiState
    data object NoHabits : NowMenuUiState
    data class NothingDoable(val reason: UnavailableReason) : NowMenuUiState
    data class Menu(val items: List<NowMenuItem>, val canLoadMore: Boolean) : NowMenuUiState
}

@HiltViewModel
class NowMenuViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val availabilityService: HabitAvailabilityService,
    private val levelDescriptionRepository: HabitLevelDescriptionRepository,
    private val triggerRepository: TriggerRepository,
    private val dismissalTracker: DismissalTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NowMenuUiState>(NowMenuUiState.Loading)
    val uiState: StateFlow<NowMenuUiState> = _uiState.asStateFlow()

    val daysWithAnyCompletion: StateFlow<Int> = triggerRepository.daysWithAnyCompletion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // The eligible set is shuffled once per refresh and held here; load more and
    // completion only move the window over it, so the order never changes underfoot.
    private var held: List<NowMenuItem> = emptyList()
    private var visibleCount = PAGE_SIZE

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun loadMore() {
        visibleCount += PAGE_SIZE
        publish()
    }

    fun complete(habitId: Long) {
        val index = held.indexOfFirst { it.habitId == habitId }
        if (index < 0) return
        val item = held[index]
        held = held.filterIndexed { i, _ -> i != index }
        if (held.isEmpty()) _uiState.value = NowMenuUiState.Loading else publish()
        viewModelScope.launch {
            val now = Instant.now()
            val triggerId = try {
                triggerRepository.insert(
                    TriggerEntity(
                        habitId = habitId,
                        scheduledAt = now,
                        firedAt = now,
                        status = TriggerStatus.COMPLETED,
                        source = SOURCE_MENU,
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to record menu completion for habit $habitId", e)
                // The row vanishing is this screen's only "done" signal, so an
                // unrecorded completion has to come back where it was.
                held = held.toMutableList().apply { add(index.coerceAtMost(size), item) }
                publish()
                return@launch
            }
            try {
                dismissalTracker.onCompleted(triggerId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The completion is already on disk; bringing the row back would
                // invite a second tap and a duplicate trigger.
                Log.e(TAG, "Failed to run promotion for menu completion $triggerId", e)
            }
            if (held.isEmpty()) load()
        }
    }

    private suspend fun load() {
        val habits = habitRepository.getAll().first()
        if (habits.isEmpty()) {
            _uiState.value = NowMenuUiState.NoHabits
            return
        }
        val availability = availabilityService.computeForAll(habits)
        val eligible = habits.filter { habit ->
            when (availability[habit.id]) {
                is AvailabilityStatus.Available, is AvailabilityStatus.NewHabit -> true
                is AvailabilityStatus.Unavailable, null -> false
            }
        }
        if (eligible.isEmpty()) {
            _uiState.value = NowMenuUiState.NothingDoable(dominantReason(availability.values))
            return
        }
        held = eligible.shuffled().map { habit ->
            NowMenuItem(
                habitId = habit.id,
                name = habit.name,
                description = levelDescriptionRepository
                    .getDescriptionForLevel(habit.id, habit.dedicationLevel)
                    ?.takeIf { it.isNotBlank() },
            )
        }
        visibleCount = PAGE_SIZE
        publish()
    }

    private fun publish() {
        _uiState.value = NowMenuUiState.Menu(
            items = held.take(visibleCount),
            canLoadMore = held.size > visibleCount,
        )
    }

    // Paused habits are excluded from the tally unless nothing else is left: "all paused"
    // is only the honest summary when there is no active habit to explain instead.
    private fun dominantReason(statuses: Collection<AvailabilityStatus>): UnavailableReason {
        val activeReasons = statuses
            .filterIsInstance<AvailabilityStatus.Unavailable>()
            .map { it.reasons }
            .filter { UnavailableReason.INACTIVE !in it }
        if (activeReasons.isEmpty()) return UnavailableReason.INACTIVE
        return activeReasons.flatten()
            .groupingBy { it }
            .eachCount()
            .entries
            .minWith(compareByDescending<Map.Entry<UnavailableReason, Int>> { it.value }.thenBy { it.key.ordinal })
            .key
    }

    companion object {
        const val PAGE_SIZE = 3
        const val SOURCE_MENU = "menu"
        private const val TAG = "NowMenuVM"
    }
}
