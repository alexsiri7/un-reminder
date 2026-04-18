package com.alexsiri7.unreminder.ui.navigation

import com.alexsiri7.unreminder.data.repository.OnboardingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isOnboarded starts null before repository emits`() = runTest {
        val fakeRepo: OnboardingRepository = mockk {
            every { isOnboardingCompleted } returns flowOf(false)
        }
        val vm = NavViewModel(fakeRepo)

        // Initial value before any emission is null (stateIn seed)
        assertNull(vm.isOnboarded.value)
    }

    @Test
    fun `isOnboarded emits false for new user`() = runTest {
        val fakeRepo: OnboardingRepository = mockk {
            every { isOnboardingCompleted } returns flowOf(false)
        }
        val vm = NavViewModel(fakeRepo)

        val emitted = mutableListOf<Boolean?>()
        val job = launch { vm.isOnboarded.toList(emitted) }
        advanceUntilIdle()
        job.cancel()

        assertTrue("Expected false emission, got: $emitted", emitted.contains(false))
    }

    @Test
    fun `isOnboarded emits true for returning user`() = runTest {
        val fakeRepo: OnboardingRepository = mockk {
            every { isOnboardingCompleted } returns flowOf(true)
        }
        val vm = NavViewModel(fakeRepo)

        val emitted = mutableListOf<Boolean?>()
        val job = launch { vm.isOnboarded.toList(emitted) }
        advanceUntilIdle()
        job.cancel()

        assertTrue("Expected true emission, got: $emitted", emitted.contains(true))
    }
}
