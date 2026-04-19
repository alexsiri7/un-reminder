package net.interstellarai.unreminder.service.llm

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.ActiveModelRepository
import net.interstellarai.unreminder.data.repository.ModelDownloadProgressRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.worker.ModelDownloadWorker
import com.google.ai.edge.litertlm.Backend
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
    /**
     * Source of truth for which model the user has selected. When absent
     * (e.g. unit tests that haven't wired this up) we fall back to
     * [ModelCatalog.default].
     */
    private val activeModelRepository: ActiveModelRepository? = null,
    /**
     * Factory for constructing the underlying LiteRT-LM engine given a model
     * path and a backend hint ("gpu" or "cpu"). Tests override this to
     * simulate GPU-init failure + CPU fallback without needing a real model
     * file or native libraries. Production defaults to [::defaultEngineFactory].
     *
     * Both the factory's parameter types and its return type are deliberately
     * kept free of `com.google.ai.edge.litertlm.*` references (we use [String]
     * for the backend hint and [Any] for the returned engine). Rationale: the
     * LiteRT-LM 0.10.2 AAR ships class-file v65 (JDK 21) but our unit-test JVM
     * runs JDK 17 both locally and in CI. When the Kotlin compiler synthesises
     * static lambda methods on a *test* class that references this factory
     * signature, those synthetic method descriptors would include the v65
     * types and JUnit's `MethodSorter.getDeclaredMethods` eagerly resolves
     * them, failing with `UnsupportedClassVersionError` before any test runs.
     * Returning [Any] lets tests stay class-version-clean; we cast to [Engine]
     * internally on the production default path.
     *
     * The default is supplied as a function reference (not an inline lambda)
     * so the litertlm types are only touched when the factory is *invoked*,
     * never when `PromptGeneratorImpl` is merely constructed. Inline lambda
     * defaults get loaded at `<init>` time by Kotlin's $default mechanism,
     * which would pull in [Engine] / [EngineConfig] / [Backend] eagerly.
     */
    private val engineFactory: (modelPath: String, cacheDir: String, backendName: String) -> Any =
        ::defaultEngineFactory,
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

    /**
     * Currently-selected descriptor. Cached at each `initialize()` entry so
     * observeDownloadProgress()'s SUCCEEDED branch can locate the correct
     * on-disk file without an extra DataStore read on the hot path.
     */
    private var activeDescriptor: ModelDescriptor = ModelCatalog.default

    override suspend fun initialize() {
        // Read the user's current selection. Fresh installs and any test
        // config that omits the repo both fall back to the catalog default.
        val desc = runCatching { activeModelRepository?.peek() }
            .getOrNull()
            ?: ModelCatalog.default
        activeDescriptor = desc

        if (ModelConfig.isPlaceholderUrl(desc.url)) {
            // Catalog entry ships with a sentinel URL (e.g. Gemma 3 is gated
            // on HF and nobody has plugged in a self-host yet). Surface as
            // Unavailable so the UI can render a descriptive message instead
            // of a perpetual "Downloading 0%".
            Log.w(
                TAG,
                "Active model '${desc.id}' has a placeholder URL — AI features disabled. " +
                    "Pick a different model in Settings or self-host this one.",
            )
            Sentry.captureMessage(
                "Active model '${desc.id}' has placeholder URL — AI features disabled",
                SentryLevel.WARNING,
            ) { scope -> scope.setTag("component", "litert-lm-init") }
            _aiStatus.value = AiStatus.Unavailable
            return
        }
        val modelFile = File(context.filesDir, desc.fileName)
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
                enqueueModelDownload(desc)
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
                enqueueModelDownload(activeDescriptor)
                observeDownloadProgress()
                _aiStatus.value = AiStatus.Downloading(0f)
                _downloadProgress.value = 0f
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to re-enqueue model download after magic-byte mismatch", e)
                _aiStatus.value = AiStatus.Failed
            }
            return
        }

        // Backend selection: try GPU first, fall back to CPU on failure.
        //
        // Why GPU first? Gemma 4 `.litertlm` containers require a backend to be
        // specified in EngineConfig or the engine throws
        // `LiteRtLmJniException: Unable to open zip archive` during load — see
        // https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md.
        // Google AI Edge Gallery's own model_allowlist.json ships Gemma 4 E2B
        // with `accelerators: "gpu,cpu"`, confirming GPU is the primary path.
        //
        // Why CPU fallback? Devices without OpenCL (emulators, stripped-down
        // vendor images) will fail GPU init. The CPU backend has no such
        // dependency, so retrying once on CPU keeps the app usable on those
        // devices. Only if both attempts fail do we surface AiStatus.Failed.
        try {
            val e = engineFactory(modelFile.absolutePath, context.cacheDir.path, "gpu") as Engine
            e.initialize()
            engine = e
            _downloadProgress.value = null
            _aiStatus.value = AiStatus.Ready
        } catch (gpuErr: Exception) {
            Log.w(TAG, "GPU backend init failed; retrying on CPU", gpuErr)
            try {
                val e = engineFactory(modelFile.absolutePath, context.cacheDir.path, "cpu") as Engine
                e.initialize()
                engine = e
                _downloadProgress.value = null
                _aiStatus.value = AiStatus.Ready
            } catch (cpuErr: Exception) {
                Log.w(TAG, "LiteRT-LM initialization failed on both GPU and CPU; AI features will be unavailable", cpuErr)
                // Attach both errors to Sentry so we can see whether it's a
                // universal-failure (bad model file) or GPU-only (device).
                Sentry.captureException(cpuErr) { scope ->
                    scope.setTag("component", "litert-lm-init")
                    scope.setTag("backend", "cpu-after-gpu-fallback")
                    scope.setExtra("gpuError", gpuErr.toString())
                }
                engine = null
                _downloadProgress.value = null
                _aiStatus.value = AiStatus.Failed
            }
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

    private fun enqueueModelDownload(desc: ModelDescriptor) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to desc.id))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ModelDownloadWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun retryModelDownload() {
        val desc = activeDescriptor
        if (ModelConfig.isPlaceholderUrl(desc.url)) {
            Log.w(TAG, "retryModelDownload: placeholder URL for '${desc.id}' — ignoring")
            return
        }
        val modelFile = File(context.filesDir, desc.fileName)
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
                .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to desc.id))
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
                                activeDescriptor.fileName,
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

/**
 * Production default for `PromptGeneratorImpl.engineFactory`. Kept as a
 * top-level function (rather than an inline lambda default) so the LiteRT-LM
 * classes ([Engine], [EngineConfig], [Backend]) are only loaded when the
 * factory is actually invoked — never at `PromptGeneratorImpl.<init>` time.
 * This keeps unit tests runnable on JDK 17 against the JDK-21-compiled
 * litertlm-android 0.10.2 AAR.
 */
private fun defaultEngineFactory(
    modelPath: String,
    cacheDir: String,
    backendName: String,
): Any {
    val backend: Backend = when (backendName) {
        "gpu" -> Backend.GPU()
        "cpu" -> Backend.CPU()
        else -> error("Unknown backend: $backendName")
    }
    return Engine(
        EngineConfig(
            modelPath = modelPath,
            cacheDir = cacheDir,
            backend = backend,
        )
    )
}
