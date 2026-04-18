package com.alexsiri7.unreminder.data.repository

import com.alexsiri7.unreminder.data.db.PendingFeedbackDao
import com.alexsiri7.unreminder.data.db.PendingFeedbackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FeedbackRepositoryTest {
    private val mockDao: PendingFeedbackDao = mockk(relaxUnitFun = true)
    private lateinit var repository: FeedbackRepository

    @Before fun setup() { repository = FeedbackRepository(mockDao) }

    @Test fun `queue inserts entity and returns id`() = runTest {
        coEvery { mockDao.insert(any()) } returns 42L
        val id = repository.queue("/path/to/screenshot.png", "the description")
        assertEquals(42L, id)
        coVerify { mockDao.insert(match { it.screenshotPath == "/path/to/screenshot.png" && it.description == "the description" }) }
    }

    @Test fun `getPending returns all entities from dao`() = runTest {
        val entities = listOf(PendingFeedbackEntity(id = 1L, screenshotPath = null, description = "desc", queuedAt = Instant.now()))
        coEvery { mockDao.getAll() } returns entities
        assertEquals(entities, repository.getPending())
    }

    @Test fun `deleteById delegates to dao`() = runTest {
        repository.deleteById(7L)
        coVerify { mockDao.deleteById(7L) }
    }
}
