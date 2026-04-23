package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitLevelDescriptionDao
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HabitLevelDescriptionRepositoryTest {

    private val dao: HabitLevelDescriptionDao = mockk(relaxUnitFun = true)
    private val repo = HabitLevelDescriptionRepository(dao)

    @Test
    fun `setDescriptions skips blank entries and preserves level index`() = runTest {
        val descriptions = listOf("", "Level 1 text", "", "", "", "Level 5 text")

        repo.setDescriptions(habitId = 10L, descriptions = descriptions)

        coVerify { dao.deleteByHabit(10L) }
        coVerify {
            dao.insertAll(listOf(
                HabitLevelDescriptionEntity(habitId = 10L, level = 1, description = "Level 1 text"),
                HabitLevelDescriptionEntity(habitId = 10L, level = 5, description = "Level 5 text"),
            ))
        }
    }

    @Test
    fun `setDescriptions with all blank strings calls insertAll with empty list`() = runTest {
        repo.setDescriptions(habitId = 10L, descriptions = listOf("", "  ", "\t"))

        coVerify { dao.deleteByHabit(10L) }
        coVerify { dao.insertAll(emptyList()) }
    }
}
