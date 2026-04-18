package com.alexsiri7.unreminder.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.OnboardingRepository
import com.alexsiri7.unreminder.data.repository.WindowRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockContext: Context = mockk(relaxed = true)
    private val mockOnboardingRepository: OnboardingRepository = mockk(relaxUnitFun = true)
    private val mockHabitRepository: HabitRepository = mockk(relaxed = true)
    private val mockWindowRepository: WindowRepository = mockk(relaxed = true)
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_DENIED

        viewModel = OnboardingViewModel(
            mockContext, mockOnboardingRepository, mockHabitRepository, mockWindowRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `skip marks onboarding completed and sets isCompleted`() = runTest {
        viewModel.skip()
        advanceUntilIdle()

        coVerify { mockOnboardingRepository.markOnboardingCompleted() }
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun `advanceToStep updates step`() = runTest {
        viewModel.advanceToStep(1)
        assertEquals(1, viewModel.uiState.value.step)

        viewModel.advanceToStep(2)
        assertEquals(2, viewModel.uiState.value.step)
    }

    @Test
    fun `completeOnboarding with blank name does not insert habit`() = runTest {
        viewModel.updateHabitName("   ")
        viewModel.completeOnboarding(saveHabit = true, saveWindow = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockHabitRepository.insert(any()) }
        coVerify { mockWindowRepository.insert(any()) }
        coVerify { mockOnboardingRepository.markOnboardingCompleted() }
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun `completeOnboarding with non-blank name inserts habit and window`() = runTest {
        viewModel.updateHabitName("Meditate")
        viewModel.completeOnboarding(saveHabit = true, saveWindow = true)
        advanceUntilIdle()

        coVerify { mockHabitRepository.insert(match { it.name == "Meditate" }) }
        coVerify { mockWindowRepository.insert(any()) }
        coVerify { mockOnboardingRepository.markOnboardingCompleted() }
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun `refreshPermissions updates permission flags`() = runTest {
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED

        viewModel.refreshPermissions()

        assertTrue(viewModel.uiState.value.hasNotificationPermission)
        assertTrue(viewModel.uiState.value.hasFineLocationPermission)
    }
}
