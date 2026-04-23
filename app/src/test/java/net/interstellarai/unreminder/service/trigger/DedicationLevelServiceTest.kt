package net.interstellarai.unreminder.service.trigger

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DedicationLevelServiceTest {

    private lateinit var habitRepository: HabitRepository
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var service: DedicationLevelService

    private val habitId = 1L

    private fun makeHabit(level: Int) = HabitEntity(
        id = habitId,
        name = "test",
        dedicationLevel = level,
        levelDescriptions = List(6) { "" },
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun makeCompletion(daysAgo: Long) = TriggerEntity(
        id = 1L,
        scheduledAt = Instant.now(),
        firedAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS),
        status = TriggerStatus.COMPLETED,
        habitId = habitId
    )

    @Before
    fun setup() {
        habitRepository = mockk(relaxUnitFun = true)
        triggerRepository = mockk()
        service = DedicationLevelService(habitRepository, triggerRepository)
    }

    @Test
    fun `returns early when habit not found`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns null

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
        coVerify(exactly = 0) { triggerRepository.getLastNForHabit(any(), any()) }
    }

    @Test
    fun `does not promote beyond MAX_LEVEL`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(DedicationLevelService.MAX_LEVEL)

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 0 always promotes on first completion`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(0)

        service.evaluatePromotion(habitId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 1 }) }
    }

    @Test
    fun `level 1 does not promote with fewer than 3 completions in 7 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(1)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            listOf(makeCompletion(1), makeCompletion(2))

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 1 promotes with exactly 3 completions in 7 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(1)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            listOf(makeCompletion(1), makeCompletion(3), makeCompletion(6))

        service.evaluatePromotion(habitId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 2 }) }
    }

    @Test
    fun `level 1 does not promote when completions are outside 7-day window`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(1)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            listOf(makeCompletion(8), makeCompletion(9), makeCompletion(10))

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 2 does not promote with fewer than 5 completions in 7 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(2)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            listOf(makeCompletion(1), makeCompletion(2), makeCompletion(3), makeCompletion(4))

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 2 promotes with exactly 5 completions in 7 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(2)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            listOf(makeCompletion(1), makeCompletion(2), makeCompletion(3), makeCompletion(4), makeCompletion(5))

        service.evaluatePromotion(habitId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 3 }) }
    }

    @Test
    fun `level 3 promotes with exactly 10 completions in 14 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(3)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            (1..10).map { makeCompletion(it.toLong()) }

        service.evaluatePromotion(habitId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 4 }) }
    }

    @Test
    fun `level 3 does not promote with stale completions outside 14-day window`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(3)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            (15L..24L).map { makeCompletion(it) }  // all outside 14-day window

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `level 4 promotes with exactly 20 completions in 30 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(4)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            (1..20).map { makeCompletion(it.toLong()) }

        service.evaluatePromotion(habitId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 5 }) }
    }

    @Test
    fun `level 4 does not promote with fewer than 20 completions in 30 days`() = runTest {
        coEvery { habitRepository.getByIdOnce(habitId) } returns makeHabit(4)
        coEvery { triggerRepository.getLastNForHabit(habitId, 60) } returns
            (1..19).map { makeCompletion(it.toLong()) }

        service.evaluatePromotion(habitId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }
}
