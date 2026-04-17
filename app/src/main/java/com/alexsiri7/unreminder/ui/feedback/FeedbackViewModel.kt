package com.alexsiri7.unreminder.ui.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.BuildConfig
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.worker.FeedbackUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float = 8f,
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f
)

data class FeedbackUiState(
    val description: String = "",
    val paths: List<DrawPath> = emptyList(),
    val currentColor: Color = Color.Red,
    val isErasing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val tokenMissing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FeedbackViewModel"
    }

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
        val pathsSnapshot = _uiState.value.paths
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            try {
                val annotatedPath = withContext(Dispatchers.IO) {
                    mergeAnnotations(screenshotPath, pathsSnapshot)
                }
                val id = feedbackRepository.queue(
                    screenshotPath = annotatedPath,
                    description = _uiState.value.description
                )
                FeedbackUploadWorker.enqueue(context, id)
                _uiState.value = _uiState.value.copy(isSubmitting = false, isSubmitted = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue feedback", e)
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = "Failed to save feedback — please try again."
                )
            }
        }
    }

    fun consumeTokenMissing() {
        _uiState.value = _uiState.value.copy(tokenMissing = false)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** Renders annotation paths onto the screenshot bitmap and returns the path to the merged file. */
    private fun mergeAnnotations(screenshotPath: String, paths: List<DrawPath>): String {
        if (paths.isEmpty()) return screenshotPath
        val src = BitmapFactory.decodeFile(screenshotPath) ?: return screenshotPath
        val mutable = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutable)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        paths.forEach { dp ->
            if (dp.canvasWidth <= 0f || dp.canvasHeight <= 0f) return@forEach
            val scaleX = mutable.width / dp.canvasWidth
            val scaleY = mutable.height / dp.canvasHeight
            paint.color = dp.color.toArgb()
            paint.strokeWidth = dp.strokeWidth * scaleX
            dp.points.zipWithNext { a, b ->
                canvas.drawLine(a.x * scaleX, a.y * scaleY, b.x * scaleX, b.y * scaleY, paint)
            }
        }
        val out = File(context.cacheDir, "feedback-annotated-${System.currentTimeMillis()}.png")
        out.outputStream().use { mutable.compress(Bitmap.CompressFormat.PNG, 90, it) }
        mutable.recycle()
        return out.absolutePath
    }
}
