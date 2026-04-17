package com.alexsiri7.unreminder

import com.alexsiri7.unreminder.data.db.PendingFeedbackDao
import com.alexsiri7.unreminder.data.db.PendingFeedbackEntity
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.match
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FeedbackRepositoryTest {

    private lateinit var dao: PendingFeedbackDao
    private lateinit var repository: FeedbackRepository

    @Before
    fun setup() {
        dao = mockk(relaxUnitFun = true)
        repository = FeedbackRepository(dao)
    }

    @Test
    fun `queue inserts pending feedback and returns id`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        val id = repository.queue(screenshotPath = "/tmp/shot.png", description = "bug found")
        assertEquals(42L, id)
        coVerify { dao.insert(match { it.screenshotPath == "/tmp/shot.png" && it.description == "bug found" }) }
    }

    @Test
    fun `getPending returns all pending items`() = runTest {
        val items = listOf(
            PendingFeedbackEntity(id = 1, screenshotPath = "/a.png", description = "a"),
            PendingFeedbackEntity(id = 2, screenshotPath = "/b.png", description = "b")
        )
        coEvery { dao.getAll() } returns items
        val result = repository.getPending()
        assertEquals(2, result.size)
        assertEquals("a", result[0].description)
    }

    @Test
    fun `getById returns matching entity`() = runTest {
        val entity = PendingFeedbackEntity(id = 5, screenshotPath = "/c.png", description = "c")
        coEvery { dao.getById(5L) } returns entity
        val result = repository.getById(5L)
        assertEquals(entity, result)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { dao.getById(99L) } returns null
        val result = repository.getById(99L)
        assertEquals(null, result)
    }

    @Test
    fun `markSent deletes by id`() = runTest {
        repository.markSent(7L)
        coVerify { dao.deleteById(7L) }
    }
}
