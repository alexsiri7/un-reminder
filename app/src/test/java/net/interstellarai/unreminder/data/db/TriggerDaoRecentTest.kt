package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class TriggerDaoRecentTest {

    private lateinit var db: AppDatabase
    private lateinit var triggerDao: TriggerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        triggerDao = db.triggerDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `getRecentTriggers excludes SCHEDULED triggers`() = runTest {
        val now = Instant.now()
        triggerDao.insert(
            TriggerEntity(scheduledAt = now, status = TriggerStatus.SCHEDULED)
        )
        triggerDao.insert(
            TriggerEntity(
                habitId = 1,
                scheduledAt = now.minusSeconds(60),
                firedAt = now.minusSeconds(30),
                status = TriggerStatus.FIRED,
                generatedPrompt = "Do the thing"
            )
        )

        val results = triggerDao.getRecentTriggers(20).first()

        assertEquals(1, results.size)
        assertEquals(TriggerStatus.FIRED, results[0].status)
        assertEquals("Do the thing", results[0].generatedPrompt)
    }

    @Test
    fun `getRecentTriggers includes COMPLETED DISMISSED and FIRED`() = runTest {
        val now = Instant.now()
        triggerDao.insert(
            TriggerEntity(
                habitId = 1,
                scheduledAt = now.minusSeconds(30),
                firedAt = now.minusSeconds(20),
                status = TriggerStatus.FIRED,
                generatedPrompt = "p1"
            )
        )
        triggerDao.insert(
            TriggerEntity(
                habitId = 2,
                scheduledAt = now.minusSeconds(20),
                firedAt = now.minusSeconds(10),
                status = TriggerStatus.COMPLETED,
                generatedPrompt = "p2"
            )
        )
        triggerDao.insert(
            TriggerEntity(
                habitId = 3,
                scheduledAt = now.minusSeconds(10),
                firedAt = now,
                status = TriggerStatus.DISMISSED,
                generatedPrompt = "p3"
            )
        )
        triggerDao.insert(
            TriggerEntity(scheduledAt = now.plusSeconds(60), status = TriggerStatus.SCHEDULED)
        )

        val results = triggerDao.getRecentTriggers(20).first()

        assertEquals(3, results.size)
        assertTrue(results.none { it.status == TriggerStatus.SCHEDULED })
    }
}
