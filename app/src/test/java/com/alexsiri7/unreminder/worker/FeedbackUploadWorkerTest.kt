package com.alexsiri7.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.alexsiri7.unreminder.data.db.PendingFeedbackEntity
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.service.github.GitHubApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test fun `doWork returns failure when endpoint URL is blank`() = runTest {
        // Under unitTests.isReturnDefaultValues = true, BuildConfig fields
        // are empty strings, so the endpoint guard fires and doWork returns
        // failure without calling the service.
        val item = PendingFeedbackEntity(
            id = 1L,
            screenshotPath = null,
            description = "test feedback",
            queuedAt = Instant.now()
        )
        coEvery { mockRepository.getPending() } returns listOf(item)
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } just Runs

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { mockGitHubApiService.submit(any(), any(), any()) }
    }

    @Test fun `doWork returns failure when the endpoint guard prevents IO attempts`() = runTest {
        val item = PendingFeedbackEntity(
            id = 2L,
            screenshotPath = null,
            description = "crash report",
            queuedAt = Instant.now()
        )
        coEvery { mockRepository.getPending() } returns listOf(item)
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } throws IOException("network unreachable")

        // Guard fires before any submit attempt, so we never reach the IOException path.
        val result = worker.doWork()
        assertEquals(Result.failure(), result)
    }

    @Test fun `WORK_NAME constant is defined`() {
        assertEquals("feedback_upload", FeedbackUploadWorker.WORK_NAME)
    }
}
