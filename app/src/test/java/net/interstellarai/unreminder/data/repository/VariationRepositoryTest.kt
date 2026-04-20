package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.VariationDao
import net.interstellarai.unreminder.data.db.VariationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class VariationRepositoryTest {
    private val mockDao: VariationDao = mockk(relaxUnitFun = true)
    private lateinit var repository: VariationRepository

    @Before fun setup() { repository = VariationRepository(mockDao) }

    @Test fun `pickRandomUnused returns null on empty pool`() = runTest {
        coEvery { mockDao.getUnusedForHabit(any(), any()) } returns emptyList()
        assertNull(repository.pickRandomUnused(1L))
    }

    @Test fun `pickRandomUnused marks returned row consumed and returns updated entity`() = runTest {
        val entity = VariationEntity(id = 42L, habitId = 1L, text = "t", promptFingerprint = "fp")
        coEvery { mockDao.getUnusedForHabit(1L, 50) } returns listOf(entity)
        coEvery { mockDao.markConsumed(eq(42L), any()) } returns 1
        val result = repository.pickRandomUnused(1L)
        assertNotNull(result)
        assertEquals(42L, result!!.id)
        assertEquals("t", result.text)
        assertNotNull(result.consumedAt)
        coVerify { mockDao.markConsumed(eq(42L), any()) }
    }

    @Test fun `pickRandomUnused returns null if markConsumed affects 0 rows`() = runTest {
        val entity = VariationEntity(id = 99L, habitId = 1L, text = "t", promptFingerprint = "fp")
        coEvery { mockDao.getUnusedForHabit(1L, 50) } returns listOf(entity)
        coEvery { mockDao.markConsumed(eq(99L), any()) } returns 0
        assertNull(repository.pickRandomUnused(1L))
    }

    @Test fun `needsRefill returns true when below threshold and false when at threshold`() = runTest {
        coEvery { mockDao.countUnused(1L) } returns 4
        assertTrue(repository.needsRefill(1L, threshold = 5))

        coEvery { mockDao.countUnused(1L) } returns 5
        assertFalse(repository.needsRefill(1L, threshold = 5))
    }

    @Test fun `needsRefill uses default threshold of 5`() = runTest {
        coEvery { mockDao.countUnused(1L) } returns 4
        assertTrue(repository.needsRefill(1L))

        coEvery { mockDao.countUnused(1L) } returns 5
        assertFalse(repository.needsRefill(1L))
    }

    @Test fun `insertAll delegates to dao insert`() = runTest {
        val variants = listOf(
            VariationEntity(habitId = 1L, text = "a", promptFingerprint = "fp1"),
            VariationEntity(habitId = 1L, text = "b", promptFingerprint = "fp2")
        )
        repository.insertAll(variants)
        coVerify { mockDao.insert(variants) }
    }

    @Test fun `deleteForHabit delegates to dao deleteByHabit`() = runTest {
        repository.deleteForHabit(42L)
        coVerify { mockDao.deleteByHabit(42L) }
    }
}
