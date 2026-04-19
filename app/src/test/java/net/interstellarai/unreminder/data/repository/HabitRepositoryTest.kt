package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitDao
import net.interstellarai.unreminder.data.db.HabitLocationCrossRefDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class HabitRepositoryTest {

    private lateinit var habitDao: HabitDao
    private lateinit var crossRefDao: HabitLocationCrossRefDao
    private lateinit var repo: HabitRepository

    @Before
    fun setup() {
        habitDao = mockk()
        crossRefDao = mockk()
        repo = HabitRepository(habitDao, crossRefDao)
    }

    @Test
    fun `getEligibleHabits with empty set uses sentinel -1L`() = runTest {
        coEvery { habitDao.getEligibleHabits(listOf(-1L), any()) } returns emptyList()

        repo.getEligibleHabits(emptySet())

        coVerify { habitDao.getEligibleHabits(listOf(-1L), any()) }
    }

    @Test
    fun `getByIdOnce delegates to dao with correct id`() = runTest {
        coEvery { habitDao.getByIdOnce(5L) } returns null

        repo.getByIdOnce(5L)

        coVerify { habitDao.getByIdOnce(5L) }
    }

    @Test
    fun `getEligibleHabits with non-empty set passes ids directly`() = runTest {
        coEvery { habitDao.getEligibleHabits(listOf(1L, 2L), any()) } returns emptyList()

        repo.getEligibleHabits(setOf(1L, 2L))

        coVerify { habitDao.getEligibleHabits(listOf(1L, 2L), any()) }
    }
}
