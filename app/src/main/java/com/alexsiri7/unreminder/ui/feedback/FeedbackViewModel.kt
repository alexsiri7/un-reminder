package com.alexsiri7.unreminder.ui.feedback

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.alexsiri7.unreminder.BuildConfig
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.service.github.GitHubApiService
import com.alexsiri7.unreminder.worker.FeedbackUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

data class FeedbackUiState(
    val screenshotBitmap: Bitmap? = null,
    val description: String = "",
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val gitHubApiService: GitHubApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "FeedbackViewModel"
    }

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun setScreenshot(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(screenshotBitmap = bitmap)
    }

    fun updateDescription(text: String) {
        _uiState.value = _uiState.value.copy(description = text)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun submit(annotationBitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            try {
                val merged = mergeAnnotations(annotationBitmap)
                val screenshotPath = merged?.let { saveToCacheDir(it) }

                if (BuildConfig.FEEDBACK_ENDPOINT_URL.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Feedback endpoint not configured."
                    )
                    return@launch
                }

                try {
                    val desc = _uiState.value.description
                    val title = desc.take(60).ifBlank { "Feedback from app" }
                    val body = buildIssueBody(desc)
                    val screenshotFile = screenshotPath?.let { File(it) }
                    gitHubApiService.submit(title, body, screenshotFile)
                    screenshotFile?.delete()
                    _uiState.value = _uiState.value.copy(isSubmitting = false, submitted = true)
                } catch (e: IOException) {
                    // Transient network failure — queue for retry when connectivity returns.
                    Log.w(TAG, "Direct submit failed (network), queuing for retry", e)
                    feedbackRepository.queue(screenshotPath, _uiState.value.description)
                    scheduleUploadWorker()
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Offline — queued for retry when connected."
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Direct submit failed (permanent)", e)
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Submission failed."
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "submit failed", e)
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Submission failed."
                )
            }
        }
    }

    private fun mergeAnnotations(annotationBitmap: Bitmap): Bitmap? {
        val screenshot = _uiState.value.screenshotBitmap ?: return null
        val merged = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(merged)
        canvas.drawBitmap(annotationBitmap, 0f, 0f, null)
        return merged
    }

    private fun saveToCacheDir(bitmap: Bitmap): String {
        val file = File(context.cacheDir, "feedback-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    private fun buildIssueBody(description: String): String =
        buildString {
            if (description.isNotBlank()) appendLine(description).appendLine()
            appendLine("---")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<FeedbackUploadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FeedbackUploadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            work
        )
    }
}
