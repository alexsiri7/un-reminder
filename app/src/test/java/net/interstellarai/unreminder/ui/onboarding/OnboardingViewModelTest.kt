package net.interstellarai.unreminder.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.OnboardingRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

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

        coVerify {
            mockHabitRepository.insert(match {
                it.name == "Meditate" &&
                it.active == true &&
                it.levelDescriptions == List(6) { "" }
            })
        }
        coVerify { mockWindowRepository.insert(any()) }
        coVerify { mockOnboardingRepository.markOnboardingCompleted() }
        assertTrue(viewModel.uiState.value.isCompleted)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `completeOnboarding with saveWindow=false does not insert window`() = runTest {
        viewModel.updateHabitName("Exercise")
        viewModel.completeOnboarding(saveHabit = true, saveWindow = false)
        advanceUntilIdle()

        coVerify { mockHabitRepository.insert(any()) }
        coVerify(exactly = 0) { mockWindowRepository.insert(any()) }
        coVerify { mockOnboardingRepository.markOnboardingCompleted() }
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun `completeOnboarding saves custom window times to WindowEntity`() = runTest {
        viewModel.updateWindowStartTime(LocalTime.of(8, 30))
        viewModel.updateWindowEndTime(LocalTime.of(20, 0))
        viewModel.completeOnboarding(saveHabit = false, saveWindow = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockHabitRepository.insert(any()) }
        coVerify {
            mockWindowRepository.insert(match {
                it.startTime == LocalTime.of(8, 30) && it.endTime == LocalTime.of(20, 0)
            })
        }
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun `completeOnboarding sets errorMessage on repository failure`() = runTest {
        coEvery { mockHabitRepository.insert(any()) } throws RuntimeException("DB error")

        viewModel.updateHabitName("Yoga")
        viewModel.completeOnboarding(saveHabit = true, saveWindow = true)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.isNotBlank())
    }

    @Test
    fun `skip sets isCompleted even if markOnboardingCompleted throws`() = runTest {
        coEvery { mockOnboardingRepository.markOnboardingCompleted() } throws RuntimeException("DataStore error")

        viewModel.skip()
        advanceUntilIdle()

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
