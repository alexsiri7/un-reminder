package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitDao
import net.interstellarai.unreminder.data.db.HabitLocationCrossRefDao
import net.interstellarai.unreminder.data.db.HabitWindowCrossRefDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class HabitRepositoryTest {

    private lateinit var habitDao: HabitDao
    private lateinit var crossRefDao: HabitLocationCrossRefDao
    private lateinit var windowCrossRefDao: HabitWindowCrossRefDao
    private lateinit var repo: HabitRepository

    @Before
    fun setup() {
        habitDao = mockk()
        crossRefDao = mockk()
        windowCrossRefDao = mockk()
        repo = HabitRepository(habitDao, crossRefDao, windowCrossRefDao)
    }

    @Test
    fun `getEligibleHabits with empty set uses sentinel -1L`() = runTest {
        coEvery { habitDao.getEligibleHabits(listOf(-1L), any(), any(), any(), any(), any()) } returns emptyList()

        repo.getEligibleHabits(emptySet())

        coVerify { habitDao.getEligibleHabits(listOf(-1L), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getByIdOnce delegates to dao with correct id`() = runTest {
        coEvery { habitDao.getByIdOnce(5L) } returns null

        repo.getByIdOnce(5L)

        coVerify { habitDao.getByIdOnce(5L) }
    }

    @Test
    fun `getEligibleHabits with non-empty set passes ids directly`() = runTest {
        coEvery { habitDao.getEligibleHabits(listOf(1L, 2L), any(), any(), any(), any(), any()) } returns emptyList()

        repo.getEligibleHabits(setOf(1L, 2L))

        coVerify { habitDao.getEligibleHabits(listOf(1L, 2L), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getEligibleHabits passes nowEpochMillis (not pre-subtracted by 3h) as third arg`() = runTest {
        val captured = slot<Long>()
        coEvery {
            habitDao.getEligibleHabits(any(), any(), capture(captured), any(), any(), any())
        } returns emptyList()

        val before = Instant.now().toEpochMilli()
        repo.getEligibleHabits(emptySet())
        val after = Instant.now().toEpochMilli()

        assertTrue(captured.captured >= before - 1000)
        assertTrue(captured.captured <= after + 1000)
        val threeHoursMillis = 3 * 3600 * 1000L
        assertTrue(captured.captured > before - threeHoursMillis + 60_000)
    }
}
