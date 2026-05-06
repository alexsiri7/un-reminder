package net.interstellarai.unreminder.ui.window

import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.WindowRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class WindowEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockWindowRepository: WindowRepository = mockk(relaxed = true)
    private lateinit var viewModel: WindowEditViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WindowEditViewModel(mockWindowRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateName updates name in uiState`() = runTest(testDispatcher) {
        viewModel.updateName("morning")
        assertEquals("morning", viewModel.uiState.value.name)
    }

    @Test
    fun `loadWindow populates name from repository`() = runTest(testDispatcher) {
        val window = WindowEntity(
            id = 1L,
            name = "morning",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(12, 0),
            daysOfWeekBitmask = 0b1111111,
        )
        every { mockWindowRepository.getById(1L) } returns flowOf<WindowEntity?>(window)

        viewModel.loadWindow(1L)
        advanceUntilIdle()

        assertEquals("morning", viewModel.uiState.value.name)
    }

    @Test
    fun `loadWindow with blank name leaves name as empty string`() = runTest(testDispatcher) {
        val window = WindowEntity(
            id = 2L,
            name = "",
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(10, 0),
            daysOfWeekBitmask = 0b1111111,
        )
        every { mockWindowRepository.getById(2L) } returns flowOf<WindowEntity?>(window)

        viewModel.loadWindow(2L)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.name)
    }

    @Test
    fun `save insert branch persists name in new WindowEntity`() = runTest(testDispatcher) {
        viewModel.updateName("evening")

        viewModel.save()
        advanceUntilIdle()

        coVerify { mockWindowRepository.insert(match { it.name == "evening" }) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save update branch persists name when editing existing window`() = runTest(testDispatcher) {
        val window = WindowEntity(
            id = 5L,
            name = "old name",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(12, 0),
            daysOfWeekBitmask = 0b1111111,
        )
        every { mockWindowRepository.getById(5L) } returns flowOf<WindowEntity?>(window)

        viewModel.loadWindow(5L)
        advanceUntilIdle()
        viewModel.updateName("new name")

        viewModel.save()
        advanceUntilIdle()

        coVerify { mockWindowRepository.update(match { it.name == "new name" && it.id == 5L }) }
        assertTrue(viewModel.uiState.value.isSaved)
    }
}
