package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.NotificationStyle
import net.interstellarai.unreminder.domain.model.VariantShape
import net.interstellarai.unreminder.ui.reminder.ReminderDetailLayout
import net.interstellarai.unreminder.widget.WidgetLayout
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class VariationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var variationDao: VariationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        habitDao = db.habitDao()
        variationDao = db.variationDao()
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun insertHabit(): Long = habitDao.insert(
        HabitEntity(name = "h")
    )

    @Test fun `insert then getUnusedForHabit returns inserted rows`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        ))
        val unused = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50)
        assertEquals(2, unused.size)
    }

    @Test fun `markConsumed excludes row from getUnusedForHabit`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        val unused = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50)
        assertTrue(unused.isEmpty())
    }

    @Test fun `markConsumed returns 1 on success and 0 for missing row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
        assertEquals(0, variationDao.markConsumed(999L, Instant.now()))
    }

    @Test fun `markConsumed returns 0 for already consumed row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
        assertEquals(0, variationDao.markConsumed(row.id, Instant.now()))
    }

    @Test fun `getById returns a consumed row and null for an unknown id`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).first()
        variationDao.markConsumed(row.id, Instant.EPOCH.plusSeconds(60))
        val consumed = variationDao.getById(row.id)
        assertEquals("v1", consumed?.text)
        assertEquals(Instant.EPOCH.plusSeconds(60), consumed?.consumedAt)
        assertNull(variationDao.getById(9999L))
    }

    @Test fun `countUnused equals inserted minus consumed`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        ))
        val row = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        assertEquals(1, variationDao.countUnused(hId))
    }

    @Test fun `deleting habit cascades to its variations`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val habit = habitDao.getByIdOnce(hId)!!
        habitDao.delete(habit)
        assertEquals(0, variationDao.countUnused(hId))
    }

    @Test fun `duplicate habitId+promptFingerprint+text insert is ignored`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        variationDao.insert(listOf(v, v))
        assertEquals(1, variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).size)
    }

    @Test fun `markConsumed writes a value readable as Instant by Room`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        variationDao.insert(listOf(v))
        val row = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).first()
        val now = Instant.now()
        variationDao.markConsumed(row.id, now)

        val cursor = db.query("SELECT consumed_at FROM variations WHERE id = ${row.id}", emptyArray())
        cursor.moveToFirst()
        val storedValue = cursor.getLong(0)
        cursor.close()
        assertEquals(now.toEpochMilli(), storedValue)
    }

    @Test fun `getUnusedForHabit respects limit parameter`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..10).map { i ->
            VariationEntity(habitId = hId, text = "v$i", promptFingerprint = "fp$i", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        })
        val result = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, limit = 3)
        assertEquals(3, result.size)
    }

    @Test fun `deleteByHabit removes only target habit variations`() = runTest {
        val h1 = insertHabit()
        val h2 = habitDao.insert(HabitEntity(name = "h2"))
        variationDao.insert(listOf(
            VariationEntity(habitId = h1, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT),
            VariationEntity(habitId = h2, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        ))
        variationDao.deleteByHabit(h1)
        assertEquals(0, variationDao.countUnused(h1))
        assertEquals(1, variationDao.countUnused(h2))
    }

    private fun shaped(habitId: Long, shape: VariantShape?, index: Int) = VariationEntity(
        habitId = habitId, text = "$shape $index", promptFingerprint = "fp",
        generatedAt = Instant.EPOCH, shape = shape,
    )

    private suspend fun consumeOne(habitId: Long, shape: VariantShape) {
        val row = variationDao.getUnusedForHabit(habitId, ActivityMode.SITTING.bit, 50).first { it.shape == shape }
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
    }

    @Test fun `getUnusedForHabit sorts rows sharing the last consumed shape after every other shape`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..3).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.QUESTION)

        repeat(20) {
            val ordered = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50)
            assertEquals(17, ordered.size)
            assertEquals(List(15) { false } + List(2) { true }, ordered.map { it.shape == VariantShape.QUESTION })
        }
    }

    @Test fun `getUnusedForHabit with limit 1 never repeats the last consumed shape while another is unused`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..3).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.TERSE)

        repeat(20) {
            assertNotEquals(VariantShape.TERSE, variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 1).single().shape)
        }
    }

    @Test fun `getUnusedForHabit follows the most recent consumption, not the first`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..3).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.QUESTION)
        consumeOne(hId, VariantShape.CHALLENGE)

        repeat(20) {
            val first = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 1).single().shape
            assertNotEquals(VariantShape.CHALLENGE, first)
        }
        assertTrue(variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).takeWhile { it.shape != VariantShape.CHALLENGE }.any { it.shape == VariantShape.QUESTION })
    }

    @Test fun `getUnusedForHabit falls back to the last consumed shape when nothing else is unused`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { shaped(hId, VariantShape.STATEMENT, it) })
        consumeOne(hId, VariantShape.STATEMENT)

        assertEquals(VariantShape.STATEMENT, variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 1).single().shape)
        assertEquals(2, variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).size)
    }

    @Test fun `getUnusedForHabit treats rows without a shape as a fresh shape`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { shaped(hId, VariantShape.STATEMENT, it) } + (1..3).map { shaped(hId, null, it) })
        consumeOne(hId, VariantShape.STATEMENT)

        repeat(20) {
            assertNull(variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 1).single().shape)
        }
    }

    @Test fun `getUnusedForHabit ignores what other habits consumed`() = runTest {
        val h1 = insertHabit()
        val h2 = habitDao.insert(HabitEntity(name = "h2"))
        variationDao.insert((1..3).map { shaped(h1, VariantShape.TERSE, it) } + (1..3).map { shaped(h2, VariantShape.TERSE, it) } + (1..3).map { shaped(h2, VariantShape.QUESTION, it) })
        consumeOne(h2, VariantShape.TERSE)

        assertEquals(3, variationDao.getUnusedForHabit(h1, ActivityMode.SITTING.bit, 50).size)
        repeat(20) {
            assertEquals(VariantShape.QUESTION, variationDao.getUnusedForHabit(h2, ActivityMode.SITTING.bit, 1).single().shape)
        }
    }

    @Test fun `deleteConsumedByHabit keeps only the most recently consumed row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..2).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.QUESTION)
        consumeOne(hId, VariantShape.TERSE)

        variationDao.deleteConsumedByHabit(hId)

        val consumed = variationDao.getRecentlyUsedFlow(hId, 50).first()
        assertEquals(listOf(VariantShape.TERSE), consumed.map { it.shape })
        assertEquals(10, variationDao.countUnused(hId))
    }

    @Test fun `deleteConsumedByHabit with nothing consumed leaves the pool alone`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { shaped(hId, VariantShape.TERSE, it) })

        variationDao.deleteConsumedByHabit(hId)

        assertEquals(3, variationDao.countUnused(hId))
    }

    // Explicit ids, so which rows share a look with the consumed one is known: a look is
    // id % 4 (notification style) or id % 5 (widget and detail layout).
    private fun withId(id: Long, habitId: Long, shape: VariantShape) = VariationEntity(
        id = id, habitId = habitId, text = "$shape $id", promptFingerprint = "fp",
        generatedAt = Instant.EPOCH, shape = shape,
    )

    // One shape only, so the shape tier is uniform and the order is the look tier's alone.
    // Consumed id 20 is 0 on both moduli, so 4, 8, 12, 16, 5, 10 and 15 repeat its look.
    @Test fun `getUnusedForHabit sorts rows that would repeat the last consumed look after the rest`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1L..20L).map { withId(it, hId, VariantShape.STATEMENT) })
        assertEquals(1, variationDao.markConsumed(20L, Instant.now()))

        repeat(20) {
            val ordered = variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50)
            assertEquals(19, ordered.size)
            assertEquals(List(12) { false } + List(7) { true }, ordered.map { it.id % 4 == 0L || it.id % 5 == 0L })
        }
    }

    // Ids 1 to 3 are fresh on both moduli; 40 repeats the consumed look on both. The fresh
    // shape still wins, so #373's guarantee is untouched.
    @Test fun `getUnusedForHabit ranks a fresh shape above a fresh look`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1L..3L).map { withId(it, hId, VariantShape.QUESTION) } + withId(20L, hId, VariantShape.QUESTION) + withId(40L, hId, VariantShape.STATEMENT))
        assertEquals(1, variationDao.markConsumed(20L, Instant.now()))

        repeat(20) {
            assertEquals(40L, variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 1).single().id)
        }
    }

    // The look tier's `id % 4` and `id % 5` literals in getUnusedForHabit mirror these enum
    // sizes (VariantTreatment.pick). Growing an enum without editing the query would leave the
    // tier mis-ranking that surface silently, so the sizes are pinned here, beside the SQL.
    @Test fun `the look tier's moduli are the surfaces' entry counts`() {
        assertEquals(4, NotificationStyle.entries.size)
        assertEquals(5, WidgetLayout.entries.size)
        assertEquals(5, ReminderDetailLayout.entries.size)
    }

    @Test fun `getUnusedForHabit with nothing consumed does not rank by look`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1L..20L).map { withId(it, hId, VariantShape.STATEMENT) })

        assertEquals(20, variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).size)
        val heads = (1..20).map { variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 1).single().id }.toSet()
        assertTrue(heads.size > 1)
    }

    private fun tagged(habitId: Long, modes: Set<ActivityMode>, shape: VariantShape, index: Int) = VariationEntity(
        habitId = habitId, text = "${modes.joinToString("+").ifEmpty { "neutral" }} $shape $index",
        promptFingerprint = "fp", generatedAt = Instant.EPOCH, shape = shape, modes = modes,
    )

    @Test fun `getUnusedForHabit sorts the current mode first, then neutral, then other modes`() = runTest {
        val hId = insertHabit()
        variationDao.insert(
            (1..3).map { tagged(hId, setOf(ActivityMode.WALKING), VariantShape.STATEMENT, it) } +
            (1..3).map { tagged(hId, emptySet(), VariantShape.STATEMENT, it) } +
            (1..3).map { tagged(hId, setOf(ActivityMode.SITTING), VariantShape.STATEMENT, it) } +
            (1..3).map { tagged(hId, setOf(ActivityMode.SITTING, ActivityMode.TRANSPORT), VariantShape.STATEMENT, it) }
        )

        repeat(20) {
            val ordered = variationDao.getUnusedForHabit(hId, ActivityMode.WALKING.bit, 50).map { it.modes }
            assertEquals(List(3) { setOf(ActivityMode.WALKING) } + List(3) { emptySet() }, ordered.take(6))
            assertTrue(ordered.drop(6).all { ActivityMode.SITTING in it })
        }
        repeat(20) {
            val ordered = variationDao.getUnusedForHabit(hId, ActivityMode.TRANSPORT.bit, 50).map { it.modes }
            assertEquals(List(3) { setOf(ActivityMode.SITTING, ActivityMode.TRANSPORT) } + List(3) { emptySet() }, ordered.take(6))
        }
    }

    @Test fun `getUnusedForHabit still answers from another mode's rows when nothing suits the moment`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { tagged(hId, setOf(ActivityMode.SITTING), VariantShape.STATEMENT, it) })

        assertEquals(3, variationDao.getUnusedForHabit(hId, ActivityMode.WALKING.bit, 50).size)
    }

    @Test fun `getUnusedForHabit rotates shape within the current mode's rows before falling back to neutral`() = runTest {
        val hId = insertHabit()
        variationDao.insert(
            (1..3).map { tagged(hId, setOf(ActivityMode.WALKING), VariantShape.QUESTION, it) } +
            (1..3).map { tagged(hId, setOf(ActivityMode.WALKING), VariantShape.TERSE, it) } +
            (1..3).map { tagged(hId, emptySet(), VariantShape.CHALLENGE, it) }
        )
        consumeOne(hId, VariantShape.QUESTION)

        repeat(20) {
            val head = variationDao.getUnusedForHabit(hId, ActivityMode.WALKING.bit, 1).single()
            assertEquals(setOf(ActivityMode.WALKING), head.modes)
            assertEquals(VariantShape.TERSE, head.shape)
        }
    }

    @Test fun `getUnusedForHabit prefers a same-shape row for the current mode over a fresh-shape neutral row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(
            (1..3).map { tagged(hId, setOf(ActivityMode.WALKING), VariantShape.QUESTION, it) } +
            (1..3).map { tagged(hId, emptySet(), VariantShape.CHALLENGE, it) }
        )
        consumeOne(hId, VariantShape.QUESTION)

        repeat(20) {
            val head = variationDao.getUnusedForHabit(hId, ActivityMode.WALKING.bit, 1).single()
            assertEquals(setOf(ActivityMode.WALKING), head.modes)
        }
    }

    private fun versioned(habitId: Long, version: Int, index: Int) = VariationEntity(
        habitId = habitId, text = "v$version #$index", promptFingerprint = "fp",
        generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT, generationVersion = version,
    )

    private suspend fun consumeText(habitId: Long, text: String) {
        val row = variationDao.getUnusedForHabit(habitId, ActivityMode.SITTING.bit, 50).first { it.text == text }
        variationDao.markConsumed(row.id, Instant.now())
    }

    @Test fun `deleteUnusedNotAtVersion removes only unconsumed rows at other versions`() = runTest {
        val hId = insertHabit()
        val other = insertHabit()
        variationDao.insert(listOf(versioned(hId, 1, 1), versioned(hId, 1, 2), versioned(hId, 2, 1), versioned(other, 1, 1)))
        consumeText(hId, "v1 #2")

        variationDao.deleteUnusedNotAtVersion(hId, 2)

        assertEquals(listOf("v2 #1"), variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).map { it.text })
        assertEquals(listOf("v1 #2"), variationDao.getRecentlyUsedFlow(hId, 50).first().map { it.text })
        assertEquals(1, variationDao.countUnused(other))
    }

    @Test fun `deleteUnusedNotAtVersion treats unversioned rows as another version`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "legacy", promptFingerprint = "fp", generatedAt = Instant.EPOCH, shape = null),
            versioned(hId, 1, 1),
        ))

        variationDao.deleteUnusedNotAtVersion(hId, 1)

        assertEquals(listOf("v1 #1"), variationDao.getUnusedForHabit(hId, ActivityMode.SITTING.bit, 50).map { it.text })
    }

    @Test fun `activeHabitIdsWithUnusedNotAtVersion lists a habit with stale unconsumed rows`() = runTest {
        val stale = insertHabit()
        val current = insertHabit()
        variationDao.insert(listOf(versioned(stale, 1, 1), versioned(stale, 2, 1), versioned(current, 2, 1)))

        assertEquals(listOf(stale), variationDao.activeHabitIdsWithUnusedNotAtVersion(2))
    }

    @Test fun `activeHabitIdsWithUnusedNotAtVersion omits a habit whose only stale row is consumed`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(versioned(hId, 1, 1), versioned(hId, 2, 1)))
        consumeText(hId, "v1 #1")

        assertTrue(variationDao.activeHabitIdsWithUnusedNotAtVersion(2).isEmpty())
    }

    @Test fun `activeHabitIdsWithUnusedNotAtVersion omits an inactive habit`() = runTest {
        val hId = habitDao.insert(HabitEntity(name = "h", active = false))
        variationDao.insert(listOf(versioned(hId, 1, 1)))

        assertTrue(variationDao.activeHabitIdsWithUnusedNotAtVersion(2).isEmpty())
    }

    @Test fun `activeHabitIdsWithUnusedNotAtVersion lists unversioned pools against any real version`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "legacy", promptFingerprint = "fp", generatedAt = Instant.EPOCH, shape = null),
        ))

        assertEquals(listOf(hId), variationDao.activeHabitIdsWithUnusedNotAtVersion(1))
    }
}
