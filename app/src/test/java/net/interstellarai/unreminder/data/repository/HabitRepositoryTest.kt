package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitDao
import net.interstellarai.unreminder.data.db.HabitLocationCrossRefDao
import net.interstellarai.unreminder.data.db.HabitWindowCrossRef
import net.interstellarai.unreminder.data.db.HabitWindowCrossRefDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
        coEvery { habitDao.getEligibleHabits(listOf(-1L), any(), any(), any()) } returns emptyList()

        repo.getEligibleHabits(emptySet())

        coVerify { habitDao.getEligibleHabits(listOf(-1L), any(), any(), any()) }
    }

    @Test
    fun `getByIdOnce delegates to dao with correct id`() = runTest {
        coEvery { habitDao.getByIdOnce(5L) } returns null

        repo.getByIdOnce(5L)

        coVerify { habitDao.getByIdOnce(5L) }
    }

    @Test
    fun `getEligibleHabits with non-empty set passes ids directly`() = runTest {
        coEvery { habitDao.getEligibleHabits(listOf(1L, 2L), any(), any(), any()) } returns emptyList()

        repo.getEligibleHabits(setOf(1L, 2L))

        coVerify { habitDao.getEligibleHabits(listOf(1L, 2L), any(), any(), any()) }
    }

    @Test
    fun `getWindowIds delegates to windowCrossRefDao with correct habit id`() = runTest {
        coEvery { windowCrossRefDao.getWindowIdsForHabit(7L) } returns listOf(10L, 20L)

        val result = repo.getWindowIds(7L)

        coVerify { windowCrossRefDao.getWindowIdsForHabit(7L) }
        assertEquals(listOf(10L, 20L), result)
    }

    @Test
    fun `setWindows calls replaceAll with correct cross refs`() = runTest {
        coEvery { windowCrossRefDao.replaceAll(99L, any()) } returns Unit

        repo.setWindows(99L, setOf(1L, 2L))

        coVerify {
            windowCrossRefDao.replaceAll(
                99L,
                listOf(
                    HabitWindowCrossRef(habitId = 99L, windowId = 1L),
                    HabitWindowCrossRef(habitId = 99L, windowId = 2L)
                )
            )
        }
    }

    @Test
    fun `setWindows with empty set calls replaceAll with empty list`() = runTest {
        coEvery { windowCrossRefDao.replaceAll(99L, emptyList()) } returns Unit

        repo.setWindows(99L, emptySet())

        coVerify { windowCrossRefDao.replaceAll(99L, emptyList()) }
    }
}
