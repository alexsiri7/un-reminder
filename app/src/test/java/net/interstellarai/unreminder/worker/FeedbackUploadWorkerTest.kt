package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import net.interstellarai.unreminder.data.db.PendingFeedbackEntity
import net.interstellarai.unreminder.data.repository.FeedbackRepository
import net.interstellarai.unreminder.service.github.GitHubApiService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class FeedbackUploadWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockRepository: FeedbackRepository = mockk(relaxUnitFun = true)
    private val mockGitHubApiService: GitHubApiService = mockk()

    private lateinit var worker: FeedbackUploadWorker

    @Before fun setup() {
        worker = FeedbackUploadWorker(
            mockContext,
            mockWorkerParams,
            mockRepository,
            mockGitHubApiService
        )
    }

    @Test fun `doWork returns success when queue is empty`() = runTest {
        coEvery { mockRepository.getPending() } returns emptyList()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test fun `doWork submits queued item and deletes on success`() = runTest {
        val item = PendingFeedbackEntity(
            id = 1L,
            screenshotPath = null,
            description = "test feedback",
            queuedAt = Instant.now()
        )
        coEvery { mockRepository.getPending() } returns listOf(item)
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { mockGitHubApiService.submit(any(), any(), any()) }
        coVerify(exactly = 1) { mockRepository.deleteById(1L) }
    }

    @Test fun `doWork returns retry on IOException`() = runTest {
        val item = PendingFeedbackEntity(
            id = 2L,
            screenshotPath = null,
            description = "crash report",
            queuedAt = Instant.now()
        )
        coEvery { mockRepository.getPending() } returns listOf(item)
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } throws IOException("network unreachable")

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        coVerify(exactly = 0) { mockRepository.deleteById(any()) }
    }

    @Test fun `doWork returns failure on non-transient exception`() = runTest {
        val item = PendingFeedbackEntity(
            id = 3L,
            screenshotPath = null,
            description = "weird failure",
            queuedAt = Instant.now()
        )
        coEvery { mockRepository.getPending() } returns listOf(item)
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } throws RuntimeException("400 bad request")

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { mockRepository.deleteById(any()) }
    }

    @Test fun `doWork body excludes log section when collectLogs returns null`() = runTest {
        val item = PendingFeedbackEntity(
            id = 1L,
            screenshotPath = null,
            description = "test feedback",
            queuedAt = Instant.now()
        )
        coEvery { mockRepository.getPending() } returns listOf(item)
        val bodySlot = slot<String>()
        coEvery { mockGitHubApiService.submit(any(), capture(bodySlot), any()) } just Runs

        worker.doWork()

        // In JVM tests collectLogs() always returns null — verify the null branch
        assertFalse(bodySlot.captured.contains("<details>"))
    }

    @Test fun `WORK_NAME constant is defined`() {
        assertEquals("feedback_upload", FeedbackUploadWorker.WORK_NAME)
    }
}
