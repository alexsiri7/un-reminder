package net.interstellarai.unreminder.service.llm

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.llm.ModelConfig
import net.interstellarai.unreminder.worker.ModelDownloadWorker
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.io.File

class PromptGeneratorImpl(private val context: Context) : PromptGenerator {

    companion object {
        private const val TAG = "PromptGenerator"
    }

    private var engine: Engine? = null

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    override suspend fun initialize() {
        if (ModelConfig.isPlaceholderUrl(BuildConfig.MODEL_CDN_URL)) {
            // Build misconfiguration: the MODEL_CDN_URL env var was not supplied at build time,
            // so the APK was built with the "https://placeholder.invalid/model.task" default.
            // Any download attempt would fail with UnknownHostException and AI features would
            // silently do nothing. Log, flag to Sentry, and skip enqueueing the download.
            Log.w(
                TAG,
                "MODEL_CDN_URL is a placeholder (${BuildConfig.MODEL_CDN_URL}) — " +
                    "AI features disabled. Set the MODEL_CDN_URL secret in CI to fix."
            )
            Sentry.captureMessage(
                "MODEL_CDN_URL is a placeholder — AI features disabled",
                SentryLevel.WARNING
            ) { scope -> scope.setTag("component", "litert-lm-init") }
            return
        }
        val modelFile = File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME)
        if (!modelFile.exists()) {
            try {
                enqueueModelDownload()
                observeDownloadProgress()
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager unavailable in this environment; model download skipped", e)
            }
            // Model not yet available — initialize() completes with engine = null
            return
        }
        try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                cacheDir = context.cacheDir.path
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            _downloadProgress.value = null
        } catch (e: Exception) {
            Log.w(TAG, "LiteRT-LM initialization failed; AI features will be unavailable", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "litert-lm-init")
            }
            engine = null
        }
    }

    private fun enqueueModelDownload() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ModelDownloadWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun observeDownloadProgress() {
        // TODO: observe WorkInfo flow and call initialize() on SUCCEEDED state so the
        // engine activates without requiring an app restart (follow-up issue).
    }

    override suspend fun generate(habit: HabitEntity, locationName: String, timeOfDay: String): String {
        val e = engine ?: return fallback(habit)
        return try {
            withTimeout(5_000) {
                val conversation = e.createConversation(ConversationConfig())
                val prompt = buildPrompt(habit, locationName, timeOfDay)
                conversation.sendMessage(prompt).toText().take(80)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "LLM generation failed, using fallback", ex)
            fallback(habit)
        }
    }

    override suspend fun generateHabitFields(title: String): AiHabitFields {
        val e = engine ?: throw IllegalStateException("LLM unavailable")
        return try {
            withTimeout(5_000) {
                val conversation = e.createConversation(ConversationConfig())
                val prompt = buildHabitFieldsPrompt(title)
                val text = conversation.sendMessage(prompt).toText()
                val lines = text.lines()
                val full = lines.firstOrNull { it.startsWith("Full:") }
                    ?.removePrefix("Full:")?.trim()
                    ?: throw IllegalStateException("Could not parse Full: line")
                val low = lines.firstOrNull { it.startsWith("Low-floor:") }
                    ?.removePrefix("Low-floor:")?.trim()
                    ?: throw IllegalStateException("Could not parse Low-floor: line")
                AiHabitFields(full, low)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "LLM habit field generation failed", ex)
            throw ex
        }
    }

    override suspend fun previewHabitNotification(habit: HabitEntity, locationName: String): String {
        val e = engine ?: throw IllegalStateException("LLM unavailable")
        return withTimeout(5_000) {
            val conversation = e.createConversation(ConversationConfig())
            val prompt = buildPrompt(habit, locationName, "now")
            conversation.sendMessage(prompt).toText().take(80)
                .ifBlank { throw IllegalStateException("Empty LLM response") }
        }
    }

    private fun buildPrompt(habit: HabitEntity, locationName: String, timeOfDay: String): String =
        """System: You are generating a one-line notification that nudges the user to do a habit. Make it warm, specific, and varied — never repeat the exact wording across calls. Maximum 80 characters. Plain text only.
        |
        |Habit: ${habit.name}
        |Full version: ${habit.fullDescription}
        |Low-floor version: ${habit.lowFloorDescription}
        |Current location: $locationName
        |Time of day: $timeOfDay""".trimMargin()

    private fun buildHabitFieldsPrompt(title: String): String =
        """System: You are generating habit description fields for a productivity app.
        |Given only a habit title, produce exactly two lines:
        |Full: <one sentence, specific full description, max 100 chars>
        |Low-floor: <minimum viable version, max 60 chars>
        |Plain text only.
        |
        |Habit title: $title""".trimMargin()

    private fun fallback(habit: HabitEntity): String =
        "${habit.name}: ${habit.lowFloorDescription}"
}

private fun com.google.ai.edge.litertlm.Message.toText(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
