package com.alexsiri7.unreminder.ui.feedback

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.BuildConfig
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.worker.FeedbackUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawPath(val points: List<Offset>, val color: Color, val strokeWidth: Float = 8f)

data class FeedbackUiState(
    val description: String = "",
    val paths: List<DrawPath> = emptyList(),
    val currentColor: Color = Color.Red,
    val isErasing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val tokenMissing: Boolean = false
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun updateDescription(text: String) {
        _uiState.value = _uiState.value.copy(description = text)
    }

    fun selectColor(color: Color) {
        _uiState.value = _uiState.value.copy(currentColor = color, isErasing = false)
    }

    fun toggleEraser() {
        _uiState.value = _uiState.value.copy(isErasing = !_uiState.value.isErasing)
    }

    fun clearPaths() {
        _uiState.value = _uiState.value.copy(paths = emptyList())
    }

    fun addPath(path: DrawPath) {
        _uiState.value = _uiState.value.copy(paths = _uiState.value.paths + path)
    }

    fun submit(screenshotPath: String) {
        if (BuildConfig.GITHUB_FEEDBACK_TOKEN.isBlank()) {
            _uiState.value = _uiState.value.copy(tokenMissing = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            val id = feedbackRepository.queue(
                screenshotPath = screenshotPath,
                description = _uiState.value.description
            )
            FeedbackUploadWorker.enqueue(context, id)
            _uiState.value = _uiState.value.copy(isSubmitting = false, isSubmitted = true)
        }
    }

    fun consumeTokenMissing() {
        _uiState.value = _uiState.value.copy(tokenMissing = false)
    }
}
