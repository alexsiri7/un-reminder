package net.interstellarai.unreminder.ui.feedback

import android.content.Context
import android.graphics.Bitmap
import net.interstellarai.unreminder.data.repository.FeedbackRepository
import net.interstellarai.unreminder.service.github.GitHubApiService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockFeedbackRepository: FeedbackRepository = mockk(relaxUnitFun = true)
    private val mockGitHubApiService: GitHubApiService = mockk()
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var viewModel: FeedbackViewModel

    @Before fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FeedbackViewModel(mockFeedbackRepository, mockGitHubApiService, mockContext)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `submit sets errorMessage when submission throws`() = runTest {
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } throws RuntimeException("boom")
        viewModel.updateDescription("app crashed on tap")
        viewModel.submit(mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitting)
        assertNotNull(state.errorMessage)
    }

    @Test fun `clearError clears errorMessage in state`() = runTest {
        coEvery { mockGitHubApiService.submit(any(), any(), any()) } throws RuntimeException("boom")
        viewModel.submit(mockk(relaxed = true))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test fun `updateDescription updates description in state`() {
        viewModel.updateDescription("some description")
        assertEquals("some description", viewModel.uiState.value.description)
    }

    @Test fun `setScreenshot updates screenshotBitmap in state`() {
        val bitmap = mockk<Bitmap>(relaxed = true)
        viewModel.setScreenshot(bitmap)
        assertEquals(bitmap, viewModel.uiState.value.screenshotBitmap)
    }
}
