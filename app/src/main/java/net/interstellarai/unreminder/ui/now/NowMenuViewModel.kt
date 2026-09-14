package net.interstellarai.unreminder.ui.now

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.DisplayTier
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.model.ActivityBasis
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.ActivityState
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.activity.ActivityRecognitionManager
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.geofence.LocationReconciler
import net.interstellarai.unreminder.service.geofence.RegistrationHealth
import net.interstellarai.unreminder.service.notification.SpriteResolver
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.ui.settings.LocationTrackingStatus
import net.interstellarai.unreminder.widget.WidgetRefresher
import java.time.Instant
import javax.inject.Inject

/**
 * One row of the menu: a peeked (not consumed) variant and its paired sprite, or — when the
 * habit's pool is empty — the dedication-level description and a sprite rotated by habit id.
 * The tier is what the row says about why it ranks where it does; every row is completable.
 */
data class NowMenuItem(
    val habitId: Long,
    val name: String,
    val text: String?,
    val variationId: Long?,
    @DrawableRes val spriteRes: Int,
    val tier: DisplayTier,
)

sealed interface NowMenuUiState {
    data object Loading : NowMenuUiState
    /** No active habit: either none exists yet or the user has paused every one. */
    data class NoHabits(val allPaused: Boolean) : NowMenuUiState
    data class Menu(val items: List<NowMenuItem>, val canLoadMore: Boolean) : NowMenuUiState
}

/** The activity the trigger pipeline would resolve right now, as the header states it. */
sealed interface ActivityReading {
    data class Observed(val mode: ActivityMode) : ActivityReading
    /** The sitting fallback standing in for an unknown activity. */
    data class Assumed(val mode: ActivityMode) : ActivityReading
    /** The one state that holds nudges back instead of narrowing them. */
    data object Cycling : ActivityReading
    data object PermissionDenied : ActivityReading
}

/** The location the trigger pipeline would resolve right now, as the header states it. */
sealed interface LocationReading {
    data class Inside(val names: List<String>) : LocationReading
    data object Outside : LocationReading
    /** Tracking cannot vouch for an inside/outside answer; Settings' status stands in for one. */
    data class Tracking(val status: LocationTrackingStatus) : LocationReading
}

data class NowContext(val activity: ActivityReading, val location: LocationReading)

@HiltViewModel
class NowMenuViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val availabilityService: HabitAvailabilityService,
    private val levelDescriptionRepository: HabitLevelDescriptionRepository,
    private val triggerRepository: TriggerRepository,
    private val variationRepository: VariationRepository,
    private val spriteResolver: SpriteResolver,
    private val dismissalTracker: DismissalTracker,
    private val widgetRefresher: WidgetRefresher,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val geofenceManager: GeofenceManager,
    private val locationRepository: LocationRepository,
    private val locationReconciler: LocationReconciler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NowMenuUiState>(NowMenuUiState.Loading)
    val uiState: StateFlow<NowMenuUiState> = _uiState.asStateFlow()

    val daysWithAnyCompletion: StateFlow<Int> = triggerRepository.daysWithAnyCompletion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Permission, staleness and battery restriction are read, not pushed, so a resume re-reads
    // them; the sensed inputs below are pushed and re-read the context on their own.
    private val resumes = MutableStateFlow(0)

    val nowContext: StateFlow<NowContext> = combine(
        resumes,
        activityRecognitionManager.lastObservation,
        geofenceManager.currentLocationIds,
        geofenceManager.registrationHealth,
        locationReconciler.reconciliationFailure,
    ) { _, _, locationIds, health, reconciliationFailure ->
        NowContext(activityReading(), locationReading(locationIds, health, reconciliationFailure))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NowContext(activityReading(), LocationReading.Tracking(LocationTrackingStatus.Checking)),
    )

    // Every active habit is shuffled within its tier and its variant peeked once per refresh
    // and held here; load more and completion only move the window over it, so neither the
    // order nor a row's words and sprite change underfoot.
    private var held: List<NowMenuItem> = emptyList()
    private var visibleCount = PAGE_SIZE

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Screen resume: re-read what the header states without reshuffling the menu. */
    fun refreshContext() {
        resumes.update { it + 1 }
    }

    /** Activity permission result: the launch-time subscription was skipped while it was denied. */
    fun onActivityPermissionResult() {
        viewModelScope.launch {
            activityRecognitionManager.requestTransitionUpdates()
            refreshContext()
        }
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
                item.variationId?.let { variationRepository.markConsumed(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The completion is already on disk; bringing the row back would
                // invite a second tap and a duplicate trigger.
                Log.e(TAG, "Failed to finish menu completion $triggerId", e)
            }
            widgetRefresher.refresh()
            if (held.isEmpty()) load()
        }
    }

    private suspend fun load() {
        val habits = habitRepository.getAll().first()
        val tiers = availabilityService.computeDisplayTiers(habits)
        if (tiers.isEmpty()) {
            _uiState.value = NowMenuUiState.NoHabits(allPaused = habits.isNotEmpty())
            return
        }
        // Cycling suppresses nothing on the menu, so its text takes the same sitting
        // fallback as an unknown activity.
        val mode = (activityRecognitionManager.resolve().state as? ActivityState.Mode)?.mode ?: ActivityMode.SITTING
        // A stable sort over a shuffle: random within each tier, tiers in order.
        held = habits.filter { it.id in tiers }
            .shuffled()
            .sortedBy { tiers.getValue(it.id) }
            .map { menuItem(it, tiers.getValue(it.id), mode) }
        visibleCount = PAGE_SIZE
        publish()
    }

    private suspend fun menuItem(habit: HabitEntity, tier: DisplayTier, mode: ActivityMode): NowMenuItem {
        val variation = variationRepository.peekUnusedVariation(habit.id, mode)
        val text = variation?.text
            ?: levelDescriptionRepository.getDescriptionForLevel(habit.id, habit.dedicationLevel)
        return NowMenuItem(
            habitId = habit.id,
            name = habit.name,
            text = text?.takeIf { it.isNotBlank() },
            variationId = variation?.id,
            spriteRes = spriteResolver.resolve(variation?.spriteTag, rotationSeed = habit.id),
            tier = tier,
        )
    }

    private fun activityReading(): ActivityReading {
        val resolution = activityRecognitionManager.resolve()
        return when (val state = resolution.state) {
            ActivityState.Cycling -> ActivityReading.Cycling
            is ActivityState.Mode -> when (resolution.basis) {
                ActivityBasis.OBSERVED -> ActivityReading.Observed(state.mode)
                ActivityBasis.ASSUMED -> ActivityReading.Assumed(state.mode)
                ActivityBasis.PERMISSION_DENIED -> ActivityReading.PermissionDenied
            }
        }
    }

    private suspend fun locationReading(
        locationIds: Set<Long>,
        health: RegistrationHealth?,
        reconciliationFailure: String?,
    ): LocationReading {
        val backgroundRestricted =
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        val status = LocationTrackingStatus.of(health, backgroundRestricted, reconciliationFailure)
        if (status != LocationTrackingStatus.Healthy) return LocationReading.Tracking(status)
        if (locationIds.isEmpty()) return LocationReading.Outside
        // A delete that died between the row and the recorded set leaves an id with no name.
        val names = locationRepository.getByIds(locationIds).map { it.name }.sorted()
        return if (names.isEmpty()) LocationReading.Outside else LocationReading.Inside(names)
    }

    private fun publish() {
        _uiState.value = NowMenuUiState.Menu(
            items = held.take(visibleCount),
            canLoadMore = held.size > visibleCount,
        )
    }

    companion object {
        const val PAGE_SIZE = 3
        const val SOURCE_MENU = "menu"
        private const val TAG = "NowMenuVM"
    }
}
