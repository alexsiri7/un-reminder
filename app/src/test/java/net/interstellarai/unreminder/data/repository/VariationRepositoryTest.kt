package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.VariationDao
import net.interstellarai.unreminder.data.db.VariationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.time.Instant

class VariationRepositoryTest {
    private val mockDao: VariationDao = mockk(relaxUnitFun = true)
    private lateinit var repository: VariationRepository

    @Before fun setup() { repository = VariationRepository(mockDao) }

    @Test fun `pickRandomUnused returns null on empty pool`() = runTest {
        coEvery { mockDao.getUnusedForHabit(any(), any()) } returns emptyList()
        assertNull(repository.pickRandomUnused(1L))
    }

    @Test fun `pickRandomUnused marks returned row consumed and returns updated entity`() = runTest {
        val entity = VariationEntity(id = 42L, habitId = 1L, text = "t", promptFingerprint = "fp", generatedAt = Instant.EPOCH)
        coEvery { mockDao.getUnusedForHabit(1L, 50) } returns listOf(entity)
        coEvery { mockDao.markConsumed(eq(42L), any()) } returns 1
        val result = repository.pickRandomUnused(1L)
        assertNotNull(result)
        assertEquals(42L, result!!.id)
        assertEquals("t", result.text)
        assertNotNull(result.consumedAt)
        coVerify { mockDao.markConsumed(eq(42L), any()) }
    }

    @Test fun `pickRandomUnused retries next candidate when markConsumed affects 0 rows`() = runTest {
        val stale = VariationEntity(id = 99L, habitId = 1L, text = "stale", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)
        val good = VariationEntity(id = 100L, habitId = 1L, text = "good", promptFingerprint = "fp2", generatedAt = Instant.EPOCH)
        coEvery { mockDao.getUnusedForHabit(1L, 50) } returns listOf(stale, good)
        coEvery { mockDao.markConsumed(eq(99L), any()) } returns 0
        coEvery { mockDao.markConsumed(eq(100L), any()) } returns 1
        val result = repository.pickRandomUnused(1L)
        assertNotNull(result)
        assertEquals(100L, result!!.id)
        coVerify { mockDao.markConsumed(eq(99L), any()) }
        coVerify { mockDao.markConsumed(eq(100L), any()) }
    }

    @Test fun `pickRandomUnused returns null when all candidates are stale`() = runTest {
        val entity = VariationEntity(id = 99L, habitId = 1L, text = "t", promptFingerprint = "fp", generatedAt = Instant.EPOCH)
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
            VariationEntity(habitId = 1L, text = "a", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = 1L, text = "b", promptFingerprint = "fp2", generatedAt = Instant.EPOCH)
        )
        repository.insertAll(variants)
        coVerify { mockDao.insert(variants) }
    }

    @Test fun `deleteForHabit delegates to dao deleteByHabit`() = runTest {
        repository.deleteForHabit(42L)
        coVerify { mockDao.deleteByHabit(42L) }
    }

    @Test fun `unusedVariationsFlow delegates to dao getUnusedFlow`() {
        val habitId = 5L
        val fakeFlow = flowOf(emptyList<VariationEntity>())
        every { mockDao.getUnusedFlow(habitId) } returns fakeFlow
        val result = repository.unusedVariationsFlow(habitId)
        assertEquals(fakeFlow, result)
    }

    @Test fun `recentlyUsedFlow uses default limit of 10`() {
        val habitId = 5L
        every { mockDao.getRecentlyUsedFlow(habitId, 10) } returns flowOf(emptyList())
        repository.recentlyUsedFlow(habitId)
        verify { mockDao.getRecentlyUsedFlow(habitId, 10) }
    }

    @Test fun `recentlyUsedFlow passes custom limit to dao`() {
        val habitId = 5L
        every { mockDao.getRecentlyUsedFlow(habitId, 3) } returns flowOf(emptyList())
        repository.recentlyUsedFlow(habitId, limit = 3)
        verify { mockDao.getRecentlyUsedFlow(habitId, 3) }
    }

    @Test fun `deleteById delegates to dao deleteById`() = runTest {
        coEvery { mockDao.deleteById(77L) } returns Unit
        repository.deleteById(77L)
        coVerify { mockDao.deleteById(77L) }
    }

    @Test fun `countTotalFlow delegates to dao countTotalFlow`() {
        val habitId = 5L
        val fakeFlow = flowOf(7)
        every { mockDao.countTotalFlow(habitId) } returns fakeFlow
        val result = repository.countTotalFlow(habitId)
        assertEquals(fakeFlow, result)
    }

    @Test fun `countConsumedSince delegates to dao countConsumedSinceFlow`() {
        val habitId = 5L
        val dayStart = java.time.Instant.EPOCH
        val fakeFlow = flowOf(3)
        every { mockDao.countConsumedSinceFlow(habitId, dayStart) } returns fakeFlow
        val result = repository.countConsumedSince(habitId, dayStart)
        assertEquals(fakeFlow, result)
    }
}
