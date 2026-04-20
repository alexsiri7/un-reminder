package net.interstellarai.unreminder.service.trigger

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DedicationLevelManagerTest {

    private lateinit var habitRepository: HabitRepository
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var manager: DedicationLevelManager

    private fun habit(level: Int, autoAdjust: Boolean = true) = HabitEntity(
        id = 1L,
        name = "test",
        fullDescription = "full",
        lowFloorDescription = "low",
        dedicationLevel = level,
        autoAdjustLevel = autoAdjust,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Before
    fun setup() {
        habitRepository = mockk(relaxUnitFun = true)
        triggerRepository = mockk()
        manager = DedicationLevelManager(habitRepository, triggerRepository)
    }

    @Test
    fun `level 0 always promotes to 1`() = runTest {
        manager.maybePromote(habit(level = 0))

        coVerify { habitRepository.update(match { it.dedicationLevel == 1 }) }
    }

    @Test
    fun `autoAdjustLevel false - no promotion`() = runTest {
        manager.maybePromote(habit(level = 0, autoAdjust = false))

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level at MAX_LEVEL - no promotion`() = runTest {
        manager.maybePromote(habit(level = 5))

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 1 with 3 completions in 7 days promotes to 2`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 3

        manager.maybePromote(habit(level = 1))

        coVerify { habitRepository.update(match { it.dedicationLevel == 2 }) }
    }

    @Test
    fun `level 1 with 2 completions in 7 days does not promote`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 2

        manager.maybePromote(habit(level = 1))

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 2 with 5 completions in 7 days promotes to 3`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 5

        manager.maybePromote(habit(level = 2))

        coVerify { habitRepository.update(match { it.dedicationLevel == 3 }) }
    }

    @Test
    fun `level 2 with 4 completions in 7 days does not promote`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 4

        manager.maybePromote(habit(level = 2))

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 3 with 10 completions in 14 days promotes to 4`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 10

        manager.maybePromote(habit(level = 3))

        coVerify { habitRepository.update(match { it.dedicationLevel == 4 }) }
    }

    @Test
    fun `level 3 with 9 completions in 14 days does not promote`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 9

        manager.maybePromote(habit(level = 3))

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 4 with 20 completions in 28 days promotes to 5`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 20

        manager.maybePromote(habit(level = 4))

        coVerify { habitRepository.update(match { it.dedicationLevel == 5 }) }
    }

    @Test
    fun `level 4 with 19 completions in 28 days does not promote`() = runTest {
        coEvery { triggerRepository.countCompletionsSince(1L, any()) } returns 19

        manager.maybePromote(habit(level = 4))

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `promotion failure is caught and does not propagate`() = runTest {
        coEvery { habitRepository.update(any()) } throws RuntimeException("db error")

        manager.maybePromote(habit(level = 0))

        // No exception propagated — test passes if we get here
        coVerify(exactly = 1) { habitRepository.update(any()) }
    }
}
