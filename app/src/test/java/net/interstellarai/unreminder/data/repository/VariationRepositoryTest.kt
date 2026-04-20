package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.VariationDao
import net.interstellarai.unreminder.data.db.VariationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test fun `pickRandomUnused marks returned row consumed`() = runTest {
        val entity = VariationEntity(id = 42L, habitId = 1L, text = "t", promptFingerprint = "fp")
        coEvery { mockDao.getUnusedForHabit(1L, 50) } returns listOf(entity)
        val result = repository.pickRandomUnused(1L)
        assertEquals(entity, result)
        coVerify { mockDao.markConsumed(eq(42L), any()) }
    }

    @Test fun `needsRefill returns true when below threshold and false when at threshold`() = runTest {
        coEvery { mockDao.countUnused(1L) } returns 4
        assertTrue(repository.needsRefill(1L, threshold = 5))

        coEvery { mockDao.countUnused(1L) } returns 5
        assertFalse(repository.needsRefill(1L, threshold = 5))
    }
}
