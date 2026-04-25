package net.interstellarai.unreminder.data.repository

import androidx.room.withTransaction
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionDao
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class HabitLevelDescriptionRepositoryTest {

    private val dao: HabitLevelDescriptionDao = mockk(relaxUnitFun = true)
    private val db: AppDatabase = mockk()
    private val repo = HabitLevelDescriptionRepository(dao, db)

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(captureLambda<suspend () -> Any?>()) } coAnswers {
            lambda<suspend () -> Any?>().captured.invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

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
