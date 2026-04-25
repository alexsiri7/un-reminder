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

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var manager: DedicationLevelManager

    private val habitId = 5L

    private fun makeHabit(
        level: Int = 0,
        autoAdjust: Boolean = true
    ) = HabitEntity(
        id = habitId,
        name = "meditation",
        dedicationLevel = level,
        autoAdjustLevel = autoAdjust,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        triggerRepository = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        manager = DedicationLevelManager(triggerRepository, habitRepository)
    }

    @Test
    fun `no-op when autoAdjustLevel is false`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(autoAdjust = false)

        manager.maybePromote(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `no-op when already at MAX_LEVEL`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(level = DedicationLevelManager.MAX_LEVEL)

        manager.maybePromote(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `no promotion below threshold`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(level = 0)
        coEvery { triggerRepository.getCompletionsSince(habitId, any()) } returns List(2) { mockk(relaxed = true) } // threshold is 3

        manager.maybePromote(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `promotes at threshold`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(level = 0)
        coEvery { triggerRepository.getCompletionsSince(habitId, any()) } returns List(3) { mockk(relaxed = true) } // threshold is 3

        manager.maybePromote(habitId)

        coVerify(exactly = 1) { habitRepository.update(match { it.dedicationLevel == 1 }) }
    }

    @Test
    fun `habit not found - no crash no update`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns null

        manager.maybePromote(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `no-op for invalid habitId`() = runTest {
        manager.maybePromote(0L)
        manager.maybePromote(-1L)

        coVerify(exactly = 0) { habitRepository.getByIdOnce(any()) }
    }
}
