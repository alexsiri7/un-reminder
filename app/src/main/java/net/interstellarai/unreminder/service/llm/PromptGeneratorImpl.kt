package net.interstellarai.unreminder.service.llm

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.ModelDownloadProgressRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.worker.ModelDownloadWorker
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

class PromptGeneratorImpl(
    private val context: Context,
    /**
     * Supplier for the [WorkInfo] progress stream. Defaults to the real
     * [WorkManager] unique-work flow; tests inject a fake to drive state
     * transitions without needing a live WorkManager.
     */
    private val workInfoFlowProvider: () -> Flow<List<WorkInfo>> = {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
    },
    /**
     * Scope the progress collector runs in. Defaults to a long-lived
     * application-scoped supervisor; tests pass a TestScope so collection
     * can be driven and cancelled deterministically.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /**
     * Optional repo for reading the last persisted download fraction on cold
     * start. Seeding from this value prevents the UI from briefly flashing
     * "0%" when the app process is killed mid-download and recreated — the
     * StateFlow inside this class would otherwise revert to `null`/`Idle`
     * until WorkManager's [WorkInfo] flow catches up. Tests omit to keep
     * the old constructor arity compatible.
     */
    private val progressRepository: ModelDownloadProgressRepository? = null,
) : PromptGenerator {

    companion object {
        private const val TAG = "PromptGenerator"
    }

    private var engine: Engine? = null

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _aiStatus = MutableStateFlow<AiStatus>(AiStatus.Idle)
    override val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()

    private var progressCollectorJob: Job? = null

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
            _aiStatus.value = AiStatus.Unavailable
            return
        }
        val modelFile = File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME)
        if (!modelFile.exists()) {
            // Seed the StateFlows from DataStore *before* enqueueing so the UI
            // shows a non-zero fraction immediately on cold start if a previous
            // worker had made progress. Without this the banner reads 0% for
            // ~1-2s until the WorkInfo flow fires its first RUNNING emission.
            try {
                val persisted = progressRepository?.peek()
                if (persisted != null && persisted in 0f..1f) {
                    _downloadProgress.value = persisted
                    _aiStatus.value = AiStatus.Downloading(persisted)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to read persisted download progress — continuing", e)
            }
            try {
                enqueueModelDownload()
                observeDownloadProgress()
            } catch (e: Throwable) {
                Log.w(TAG, "WorkManager unavailable in this environment; model download skipped", e)
            }
            // Model not yet available — initialize() completes with engine = null.
            // The progress collector will re-trigger initializeEngineFromFile() on SUCCEEDED.
            return
        }
        initializeEngineFromFile(modelFile)
    }

    internal fun initializeEngineFromFile(modelFile: File) {
        if (engine != null) return

        // Pre-flight magic-byte check. The LiteRT-LM engine throws a cryptic
        // `LiteRtLmJniException: Unable to open zip archive` on any corrupt
        // model file, without ever deleting it — so the next app start would
        // simply re-throw the same JNI exception. Catch obvious corruption
        // here (HTML error body, truncated download that somehow slipped past
        // the worker, leftover partial from a previous build) and re-enqueue
        // the download cleanly.
        if (!modelFileLooksValid(modelFile)) {
            val hex = readFirst8(modelFile).joinToString(" ") { "%02x".format(it) }
            Log.w(
                TAG,
                "Model file magic looks wrong (got: $hex) — deleting and re-enqueuing download",
            )
            modelFile.delete()
            try {
                enqueueModelDownload()
                observeDownloadProgress()
                _aiStatus.value = AiStatus.Downloading(0f)
                _downloadProgress.value = 0f
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to re-enqueue model download after magic-byte mismatch", e)
                _aiStatus.value = AiStatus.Failed
            }
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
            _aiStatus.value = AiStatus.Ready
        } catch (e: Exception) {
            Log.w(TAG, "LiteRT-LM initialization failed; AI features will be unavailable", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "litert-lm-init")
            }
            engine = null
            _downloadProgress.value = null
            _aiStatus.value = AiStatus.Failed
        }
    }

    /**
     * Cheap pre-check: does [modelFile] start with `LITE…` or `PK`? A negative
     * result is a strong signal that the file on disk is not a LiteRT-LM
     * container (see ModelDownloadWorker for the full magic-byte list and
     * rationale).
     */
    private fun modelFileLooksValid(modelFile: File): Boolean {
        if (!modelFile.exists() || modelFile.length() < 4) return false
        val first8 = readFirst8(modelFile)
        val isLitertlm = first8.take(4).toByteArray()
            .contentEquals(byteArrayOf(0x4C, 0x49, 0x54, 0x45)) // "LITE"
        val isZip = first8.take(2).toByteArray()
            .contentEquals(byteArrayOf(0x50, 0x4B)) // "PK"
        return isLitertlm || isZip
    }

    private fun readFirst8(modelFile: File): ByteArray {
        val buf = ByteArray(8)
        return try {
            modelFile.inputStream().use { it.read(buf) }
            buf
        } catch (_: Exception) {
            ByteArray(8)
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

    override fun retryModelDownload() {
        if (ModelConfig.isPlaceholderUrl(BuildConfig.MODEL_CDN_URL)) {
            Log.w(TAG, "retryModelDownload: placeholder URL — ignoring")
            return
        }
        val modelFile = File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME)
        if (modelFile.exists()) {
            // File already on disk — re-attempt engine init instead of re-downloading.
            initializeEngineFromFile(modelFile)
            return
        }
        try {
            // REPLACE so a failed/cancelled prior run doesn't block the retry.
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ModelDownloadWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            observeDownloadProgress()
        } catch (e: Throwable) {
            Log.w(TAG, "retryModelDownload: WorkManager unavailable", e)
        }
    }

    /**
     * Wires WorkManager's per-worker progress + state stream into [_downloadProgress]
     * and [_aiStatus]. Idempotent — if a collector is already running, does nothing.
     *
     * Progress key note: [ModelDownloadWorker] emits an Int percent (0..100) under
     * [ModelDownloadWorker.KEY_PROGRESS], not a float fraction. We normalise to
     * a 0.0..1.0 Float here so UI can drive [androidx.compose.material3.LinearProgressIndicator]
     * directly.
     */
    internal fun observeDownloadProgress() {
        val existing = progressCollectorJob
        if (existing != null && existing.isActive) return
        progressCollectorJob = scope.launch {
            try {
                workInfoFlowProvider().collect { infos ->
                    // getWorkInfosForUniqueWorkFlow returns the list for this unique-work
                    // name; under normal operation there is at most one active entry.
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.RUNNING -> {
                            val pct = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, -1)
                            if (pct in 0..100) {
                                val fraction = pct / 100f
                                _downloadProgress.value = fraction
                                _aiStatus.value = AiStatus.Downloading(fraction)
                            } else {
                                // RUNNING but no progress datum yet (initial HTTP handshake);
                                // show an indeterminate 0% so the banner can render.
                                if (_downloadProgress.value == null) {
                                    _downloadProgress.value = 0f
                                    _aiStatus.value = AiStatus.Downloading(0f)
                                }
                            }
                        }
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            // Not yet started (e.g. waiting for network). Keep banner hidden
                            // unless we were already showing one.
                            if (_downloadProgress.value == null) {
                                _aiStatus.value = AiStatus.Downloading(0f)
                                _downloadProgress.value = 0f
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _downloadProgress.value = null
                            // Transition to engine init. initializeEngineFromFile guards
                            // against double-init.
                            val modelFile = File(
                                context.filesDir,
                                ModelDownloadWorker.MODEL_FILENAME,
                            )
                            if (modelFile.exists()) {
                                initializeEngineFromFile(modelFile)
                            } else {
                                // Unusual: worker reported success but file is missing.
                                Log.w(TAG, "Download SUCCEEDED but model file missing")
                                _aiStatus.value = AiStatus.Failed
                            }
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            Log.w(
                                TAG,
                                "Model download ${info.state} — AI remains unavailable " +
                                    "(WorkManager's own retry policy applies for FAILED)",
                            )
                            _downloadProgress.value = null
                            _aiStatus.value = AiStatus.Failed
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "observeDownloadProgress: collector crashed", e)
            }
        }
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
