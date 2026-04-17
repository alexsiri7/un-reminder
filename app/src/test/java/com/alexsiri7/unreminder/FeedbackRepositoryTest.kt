package com.alexsiri7.unreminder

import com.alexsiri7.unreminder.data.db.PendingFeedbackDao
import com.alexsiri7.unreminder.data.db.PendingFeedbackEntity
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
        coVerify { dao.insert(any()) }
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
    fun `markSent deletes by id`() = runTest {
        repository.markSent(7L)
        coVerify { dao.deleteById(7L) }
    }
}
