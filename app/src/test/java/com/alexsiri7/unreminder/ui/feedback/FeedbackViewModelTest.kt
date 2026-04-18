package com.alexsiri7.unreminder.ui.feedback

import android.content.Context
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.service.github.GitHubApiService
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test fun `submit sets errorMessage when token is blank`() = runTest {
        viewModel.updateDescription("app crashed on tap")
        viewModel.submit(mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitting)
        assertNotNull(state.errorMessage)
    }
}
