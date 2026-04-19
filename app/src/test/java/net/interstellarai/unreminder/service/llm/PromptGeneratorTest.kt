package net.interstellarai.unreminder.service.llm

import android.content.Context
import androidx.work.Data
import androidx.work.WorkInfo
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.worker.ModelDownloadWorker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

class PromptGeneratorTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var tempDir: File
    private lateinit var generator: PromptGeneratorImpl

    private val habit = HabitEntity(
        id = 1,
        name = "meditation",
        fullDescription = "20-minute guided meditation",
        lowFloorDescription = "3 deep breaths",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        tempDir = createTempDir()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        generator = PromptGeneratorImpl(context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- null-engine paths (engine not initialized) ---

    @Test
    fun `generate returns fallback when engine is null`() = runTest {
        val result = generator.generate(habit, "Home", "morning")
        assertEquals("meditation: 3 deep breaths", result)
    }

    @Test
    fun `generate returns fallback with empty low floor description`() = runTest {
        val emptyHabit = habit.copy(id = 2, name = "exercise", lowFloorDescription = "")
        val result = generator.generate(emptyHabit, "Work", "afternoon")
        assertEquals("exercise: ", result)
    }

    @Test
    fun `fallback format is name colon lowFloorDescription`() = runTest {
        val customHabit = habit.copy(name = "reading", lowFloorDescription = "read one page")
        val result = generator.generate(customHabit, "any location", "evening")
        assertEquals("reading: read one page", result)
    }

    @Test
    fun `generateHabitFields throws when engine is null`() = runTest {
        val result = runCatching { generator.generateHabitFields("meditation") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("LLM unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `previewHabitNotification throws when engine is null`() = runTest {
        val result = runCatching { generator.previewHabitNotification(habit, "Anywhere") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("LLM unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `downloadProgress is null when model file is absent`() = runTest {
        assertNull(generator.downloadProgress.value)
        generator.initialize()
        assertNull(generator.downloadProgress.value)
    }

    // --- corrupt model file pre-check ---
    //
    // Regression guard for the on-device `LiteRtLmJniException: Unable to open
    // zip archive` bug. If a previous run left a truncated/HTML body under the
    // model filename, the old code would feed it to `Engine.initialize()` and
    // fail the same way on every app launch. The new pre-check must delete the
    // file before attempting engine init so the re-download can overwrite it.

    @Test
    fun `initializeEngineFromFile deletes model file with bogus magic bytes`() = runTest {
        // Write an HTML-error-page body under the model filename — matches the
        // worst-case field scenario where a misconfigured CDN returned 200 OK
        // with a maintenance page instead of the model bytes. In that state,
        // the old code would hand the corrupt file straight to LiteRT-LM,
        // catch the JNI exception, and leave the bad file on disk to fail the
        // same way on every subsequent app launch.
        val modelFile = File(tempDir, ModelDownloadWorker.MODEL_FILENAME)
        modelFile.writeBytes("<!DOCTYPE html><html>oops</html>".toByteArray())
        assertTrue("precondition: corrupt model file must exist", modelFile.exists())

        // Bypass initialize() (which short-circuits on the placeholder
        // MODEL_CDN_URL in test BuildConfig) and exercise the pre-init check
        // directly. WorkManager is unavailable here, so the re-enqueue attempt
        // will throw and the catch-branch sets aiStatus = Failed — that's OK,
        // the contract under test is "corrupt file is removed".
        generator.initializeEngineFromFile(modelFile)

        assertTrue(
            "corrupt model file must be deleted so a fresh download can land",
            !modelFile.exists(),
        )
        val status = generator.aiStatus.value
        assertTrue(
            "aiStatus must not be Ready after corrupt-file cleanup (was $status)",
            status !is AiStatus.Ready,
        )
    }

    // --- observeDownloadProgress: WorkManager flow wiring ---
    //
    // PromptGeneratorImpl takes an injectable workInfoFlowProvider so tests can
    // drive the state-transition logic without a live WorkManager. The test
    // mirrors the collection contract documented on observeDownloadProgress and
    // asserts the RUNNING-percent → Float-fraction mapping plus the SUCCESS → null
    // transition the spec calls out.

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observeDownloadProgress maps WorkInfo stream to Float fraction and nulls on SUCCESS`() = runTest {
        val fakeFlow = MutableSharedFlow<List<WorkInfo>>(replay = 1, extraBufferCapacity = 8)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gen = PromptGeneratorImpl(
            context = context,
            workInfoFlowProvider = { fakeFlow },
            scope = CoroutineScope(dispatcher),
        )

        val progressSamples = mutableListOf<Float?>()
        val statusSamples = mutableListOf<AiStatus>()
        val watchJob = launch(dispatcher) { gen.downloadProgress.collect { progressSamples.add(it) } }
        val statusJob = launch(dispatcher) { gen.aiStatus.collect { statusSamples.add(it) } }

        gen.observeDownloadProgress()
        dispatcher.scheduler.advanceUntilIdle()

        val id = UUID.randomUUID()
        fakeFlow.emit(listOf(buildWorkInfo(id, WorkInfo.State.RUNNING, percent = 0)))
        dispatcher.scheduler.advanceUntilIdle()
        fakeFlow.emit(listOf(buildWorkInfo(id, WorkInfo.State.RUNNING, percent = 50)))
        dispatcher.scheduler.advanceUntilIdle()
        fakeFlow.emit(listOf(buildWorkInfo(id, WorkInfo.State.RUNNING, percent = 100)))
        dispatcher.scheduler.advanceUntilIdle()
        fakeFlow.emit(listOf(buildWorkInfo(id, WorkInfo.State.SUCCEEDED, percent = null)))
        dispatcher.scheduler.advanceUntilIdle()

        // First sample is the initial null state from the StateFlow.
        assertEquals(null, progressSamples.first())
        // Fractions 0.0, 0.5, 1.0 should all have been pushed in order.
        assertTrue(
            "expected 0.0f in progress samples but got $progressSamples",
            progressSamples.contains(0.0f),
        )
        assertTrue(
            "expected 0.5f in progress samples but got $progressSamples",
            progressSamples.contains(0.5f),
        )
        assertTrue(
            "expected 1.0f in progress samples but got $progressSamples",
            progressSamples.contains(1.0f),
        )
        // SUCCESS nulls out downloadProgress.
        assertNull("progress should be null after SUCCEEDED", progressSamples.last())
        // Status transitions: Downloading(.5f) → Downloading(1f) → Ready
        // (engine init will fail because the model file isn't real, so status
        // ends at Failed, not Ready — that's fine, we just assert the
        // Downloading fractions show up).
        assertTrue(
            "expected Downloading(0.5f) in status samples but got $statusSamples",
            statusSamples.any { it is AiStatus.Downloading && it.fraction == 0.5f },
        )

        watchJob.cancel()
        statusJob.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observeDownloadProgress sets AiStatus Failed on FAILED terminal state`() = runTest {
        val fakeFlow = MutableSharedFlow<List<WorkInfo>>(replay = 1, extraBufferCapacity = 8)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gen = PromptGeneratorImpl(
            context = context,
            workInfoFlowProvider = { fakeFlow },
            scope = CoroutineScope(dispatcher),
        )

        gen.observeDownloadProgress()
        dispatcher.scheduler.advanceUntilIdle()
        fakeFlow.emit(listOf(buildWorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, percent = null)))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(gen.downloadProgress.value)
        assertEquals(AiStatus.Failed, gen.aiStatus.value)
    }

    private fun buildWorkInfo(
        id: UUID,
        state: WorkInfo.State,
        percent: Int?,
    ): WorkInfo {
        val info: WorkInfo = mockk(relaxed = true)
        every { info.id } returns id
        every { info.state } returns state
        val progressData = if (percent != null) {
            Data.Builder().putInt(ModelDownloadWorker.KEY_PROGRESS, percent).build()
        } else {
            Data.EMPTY
        }
        every { info.progress } returns progressData
        return info
    }
}
