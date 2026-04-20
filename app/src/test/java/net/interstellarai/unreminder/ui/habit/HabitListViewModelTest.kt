package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockPromptGenerator: PromptGenerator = mockk()
    private val mockHabitRepository: HabitRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockHabitRepository.getAll() } returns flowOf(emptyList())
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HabitListViewModel(mockHabitRepository, mockPromptGenerator)

    @Test
    fun `aiStatus delegates directly to promptGenerator aiStatus`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Unavailable)
        val vm = buildViewModel()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
    }

    @Test
    fun `aiStatus is Ready when promptGenerator reports Ready`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
        val vm = buildViewModel()
        assertEquals(AiStatus.Ready, vm.aiStatus.value)
    }
}
