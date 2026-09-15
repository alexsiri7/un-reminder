package net.interstellarai.unreminder.ui.reminder

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.di.IoDispatcher
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.domain.model.VariantTreatment
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.notification.SpriteResolver
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.widget.PullCompletionRecorder
import net.interstellarai.unreminder.widget.WidgetRefresher
import javax.inject.Inject

data class ReminderDetailUiState(
    val promptText: String = "",
    val habitName: String = "",
    val dedicationLevel: Int = 0,
    val videoUrl: String? = null,
    /** The mascot that led here; null when the row it was read from is gone. */
    @DrawableRes val spriteRes: Int? = null,
    val layout: ReminderDetailLayout = ReminderDetailLayout.SPRITE_TOP,
    /** Set once a load finishes; null until then, so nothing can be completed. */
    val target: ReminderDetailTarget? = null,
    val isLoading: Boolean = true,
    val isDone: Boolean = false,
    val isProcessing: Boolean = false,
    val canComplete: Boolean = false,
)

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val variationRepository: VariationRepository,
    private val levelDescriptionRepository: HabitLevelDescriptionRepository,
    private val dismissalTracker: DismissalTracker,
    private val notificationHelper: NotificationHelper,
    private val widgetRefresher: WidgetRefresher,
    private val completionRecorder: PullCompletionRecorder,
    private val spriteResolver: SpriteResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderDetailUiState())
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    fun init(triggerId: Long) {
        val target = ReminderDetailTarget.Trigger(triggerId)
        viewModelScope.launch(ioDispatcher) {
            try {
                val trigger = triggerRepository.getById(triggerId)
                val habit = trigger?.habitId?.let { habitRepository.getByIdOnce(it) }
                // The variant the notification showed; a row with no habit never fired and has
                // nothing to key on.
                val seed = trigger?.let { t -> t.habitId?.let { VariantTreatment.seed(t.variationId, it) } } ?: triggerId
                val canComplete = when (trigger?.status) {
                    TriggerStatus.FIRED -> {
                        // Opened from a live notification. The Open action does not auto-cancel it,
                        // and OPENED never reaches the DismissalTracker (#378). A declined write
                        // means a swipe or Later resolved the trigger first, so Did it must hide.
                        val opened = triggerRepository.recordOutcome(triggerId, TriggerStatus.OPENED)
                        notificationHelper.cancelNotification(triggerId)
                        opened
                    }
                    TriggerStatus.OPENED -> true
                    else -> false
                }
                _uiState.value = ReminderDetailUiState(
                    target = target,
                    promptText = trigger?.generatedPrompt ?: "",
                    habitName = habit?.name ?: "",
                    dedicationLevel = habit?.dedicationLevel ?: 0,
                    videoUrl = videoUrlOf(trigger?.actionUrl),
                    // The same call, with the same seed, the notification made when it was
                    // posted, so the mascot is the one that led here.
                    spriteRes = trigger?.let { spriteResolver.resolve(it.spriteTag, rotationSeed = triggerId) },
                    layout = ReminderDetailLayout.forSeed(seed),
                    isLoading = false,
                    canComplete = canComplete,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load trigger $triggerId", e)
                _uiState.value = _uiState.value.copy(isLoading = false, layout = ReminderDetailLayout.forSeed(triggerId))
            }
        }
    }

    /**
     * A row opened from the Now page or the widget. Nothing is recorded: OPENED is the
     * notification path's signal (#378), and no trigger is synthesised for a pull.
     */
    fun initVariant(habitId: Long, variationId: Long?) {
        val target = ReminderDetailTarget.Variant(habitId, variationId)
        val layout = ReminderDetailLayout.forSeed(VariantTreatment.seed(variationId, habitId))
        viewModelScope.launch(ioDispatcher) {
            try {
                val habit = habitRepository.getByIdOnce(habitId)
                val variation = variationId?.let { variationRepository.getById(it) }
                val promptText = variation?.text
                    ?: habit?.let { levelDescriptionRepository.getDescriptionForLevel(habitId, it.dedicationLevel) }
                    ?: ""
                _uiState.value = ReminderDetailUiState(
                    target = target,
                    promptText = promptText,
                    habitName = habit?.name ?: "",
                    dedicationLevel = habit?.dedicationLevel ?: 0,
                    videoUrl = videoUrlOf(variation?.actionUrl),
                    // Seeded by habit id like the Now row and the widget, so the mascot agrees
                    // with the row that was tapped.
                    spriteRes = habit?.let { spriteResolver.resolve(variation?.spriteTag, rotationSeed = habitId) },
                    layout = layout,
                    isLoading = false,
                    canComplete = habit != null,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load variant $variationId for habit $habitId", e)
                _uiState.value = _uiState.value.copy(isLoading = false, layout = layout)
            }
        }
    }

    fun markCompleted() {
        val target = _uiState.value.target ?: return
        if (_uiState.value.isProcessing) return
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                when (target) {
                    is ReminderDetailTarget.Trigger -> completeTrigger(target.triggerId)
                    is ReminderDetailTarget.Variant -> completeVariant(target)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to record completion for $target", e)
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    private suspend fun completeTrigger(triggerId: Long) {
        val recorded = triggerRepository.recordOutcome(triggerId, TriggerStatus.COMPLETED)
        if (recorded) {
            dismissalTracker.onCompleted(triggerId)
            widgetRefresher.refresh()
        }
        notificationHelper.cancelNotification(triggerId)
        // A declined write means a swipe or Later resolved the trigger since the screen
        // loaded: nothing was recorded, so withdraw the chip rather than leave as if
        // the tap had worked.
        _uiState.value = if (recorded) {
            _uiState.value.copy(isDone = true)
        } else {
            _uiState.value.copy(canComplete = false)
        }
    }

    private suspend fun completeVariant(target: ReminderDetailTarget.Variant) {
        completionRecorder.complete(target.habitId, target.variationId, SOURCE_DETAIL)
        widgetRefresher.refresh()
        _uiState.value = _uiState.value.copy(isDone = true)
    }

    private fun videoUrlOf(actionUrl: String?): String? = actionUrl?.takeIf { it.startsWith("https://") }

    companion object {
        const val SOURCE_DETAIL = "detail"
        private const val TAG = "ReminderDetailVM"
    }
}
