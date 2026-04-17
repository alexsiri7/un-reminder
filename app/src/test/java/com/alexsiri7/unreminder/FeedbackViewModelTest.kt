package com.alexsiri7.unreminder

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.ui.feedback.FeedbackViewModel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedbackViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: FeedbackRepository
    private lateinit var viewModel: FeedbackViewModel

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        repository = mockk(relaxUnitFun = true)
        viewModel = FeedbackViewModel(context, repository)
    }

    @Test
    fun `updateDescription updates state`() = runTest {
        viewModel.updateDescription("crash on tap")
        assertEquals("crash on tap", viewModel.uiState.value.description)
    }

    @Test
    fun `selectColor changes current color and disables eraser`() = runTest {
        viewModel.toggleEraser()
        assertTrue(viewModel.uiState.value.isErasing)
        viewModel.selectColor(Color.Yellow)
        assertEquals(Color.Yellow, viewModel.uiState.value.currentColor)
        assertFalse(viewModel.uiState.value.isErasing)
    }

    @Test
    fun `toggleEraser flips erasing state`() = runTest {
        assertFalse(viewModel.uiState.value.isErasing)
        viewModel.toggleEraser()
        assertTrue(viewModel.uiState.value.isErasing)
        viewModel.toggleEraser()
        assertFalse(viewModel.uiState.value.isErasing)
    }

    @Test
    fun `clearPaths empties path list`() = runTest {
        viewModel.addPath(com.alexsiri7.unreminder.ui.feedback.DrawPath(
            listOf(androidx.compose.ui.geometry.Offset(0f, 0f)),
            Color.Red
        ))
        assertEquals(1, viewModel.uiState.value.paths.size)
        viewModel.clearPaths()
        assertTrue(viewModel.uiState.value.paths.isEmpty())
    }

    @Test
    fun `submit sets tokenMissing when token is blank`() = runTest {
        // BuildConfig.GITHUB_FEEDBACK_TOKEN defaults to empty string in test
        viewModel.submit("/tmp/shot.png")
        assertTrue(viewModel.uiState.value.tokenMissing)
    }
}
