package net.interstellarai.unreminder.service.trigger

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DismissalTrackerTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var tracker: DismissalTracker

    private val habitId = 5L
    private val triggerId = 42L

    private fun makeTrigger(status: TriggerStatus, hId: Long? = habitId) = TriggerEntity(
        id = triggerId,
        scheduledAt = Instant.now(),
        firedAt = Instant.now(),
        status = status,
        habitId = hId
    )

    private fun makeDismissedTrigger(id: Long = triggerId) = TriggerEntity(
        id = id,
        scheduledAt = Instant.now(),
        firedAt = Instant.now(),
        status = TriggerStatus.DISMISSED,
        habitId = habitId
    )

    private val testHabit = HabitEntity(
        id = habitId,
        name = "meditation",
        dedicationLevel = 0,
        active = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        triggerRepository = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)
        tracker = DismissalTracker(triggerRepository, habitRepository, notificationHelper)
    }

    @Test
    fun `trigger not found - skips check`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns null

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { triggerRepository.getLastNForHabit(any(), any()) }
    }

    @Test
    fun `trigger has no habitId - skips check`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED, hId = null)

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { triggerRepository.getLastNForHabit(any(), any()) }
    }

    @Test
    fun `fewer than 3 triggers - no deactivation`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41))

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `3 triggers but not all dismissed - no deactivation`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns listOf(
            makeDismissedTrigger(42),
            TriggerEntity(id = 41, scheduledAt = Instant.now(), firedAt = Instant.now(), status = TriggerStatus.COMPLETED, habitId = habitId),
            makeDismissedTrigger(40)
        )

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `3 consecutive dismissals - habit deactivated and notification posted`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41), makeDismissedTrigger(40))
        coEvery { habitRepository.getByIdOnce(habitId) } returns testHabit

        tracker.onDismissed(triggerId)

        coVerify { habitRepository.update(match { !it.active }) }
        coVerify { notificationHelper.postHabitPausedNotification(habitId, "meditation") }
    }

    @Test
    fun `3 consecutive dismissals but habit already paused - no notification`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41), makeDismissedTrigger(40))
        coEvery { habitRepository.getByIdOnce(habitId) } returns testHabit.copy(active = false)

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
        coVerify(exactly = 0) { notificationHelper.postHabitPausedNotification(any(), any()) }
    }

    @Test
    fun `3 consecutive dismissals but habit not found - no crash`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41), makeDismissedTrigger(40))
        coEvery { habitRepository.getByIdOnce(habitId) } returns null

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
        coVerify(exactly = 0) { notificationHelper.postHabitPausedNotification(any(), any()) }
    }

    @Test
    fun `3 consecutive dismissals at level 2 - demotes to level 1, no pause notification`() = runTest {
        val habitAtLevel2 = testHabit.copy(dedicationLevel = 2, autoAdjustLevel = true)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41), makeDismissedTrigger(40))
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitAtLevel2

        tracker.onDismissed(triggerId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 1 && it.active }) }
        coVerify(exactly = 0) { notificationHelper.postHabitPausedNotification(any(), any()) }
    }

    @Test
    fun `3 consecutive dismissals at level 1 with autoAdjustLevel false - no action`() = runTest {
        val habitNoAuto = testHabit.copy(dedicationLevel = 1, autoAdjustLevel = false)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41), makeDismissedTrigger(40))
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitNoAuto

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
        coVerify(exactly = 0) { notificationHelper.postHabitPausedNotification(any(), any()) }
    }

    @Test
    fun `3 consecutive dismissals at level 0 with autoAdjustLevel false - no action`() = runTest {
        val habitNoAuto = testHabit.copy(dedicationLevel = 0, autoAdjustLevel = false)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.DISMISSED)
        coEvery { triggerRepository.getLastNForHabit(habitId, 3) } returns
            listOf(makeDismissedTrigger(42), makeDismissedTrigger(41), makeDismissedTrigger(40))
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitNoAuto

        tracker.onDismissed(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
        coVerify(exactly = 0) { notificationHelper.postHabitPausedNotification(any(), any()) }
    }

    // --- onCompleted tests ---

    @Test
    fun `onCompleted promotes level 0 habit immediately`() = runTest {
        val habitAtLevel0 = testHabit.copy(dedicationLevel = 0, autoAdjustLevel = true)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED)
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitAtLevel0

        tracker.onCompleted(triggerId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 1 }) }
    }

    @Test
    fun `onCompleted promotes level 1 habit when 3+ completions in 7 days`() = runTest {
        val habitAtLevel1 = testHabit.copy(dedicationLevel = 1, autoAdjustLevel = true)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED)
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitAtLevel1
        coEvery { triggerRepository.getCompletionsSince(habitId, any()) } returns listOf(
            makeTrigger(TriggerStatus.COMPLETED, triggerId + 1),
            makeTrigger(TriggerStatus.COMPLETED, triggerId + 2),
            makeTrigger(TriggerStatus.COMPLETED, triggerId + 3),
        )

        tracker.onCompleted(triggerId)

        coVerify { habitRepository.update(match { it.dedicationLevel == 2 }) }
    }

    @Test
    fun `onCompleted does NOT promote level 1 habit when fewer than 3 completions in 7 days`() = runTest {
        val habitAtLevel1 = testHabit.copy(dedicationLevel = 1, autoAdjustLevel = true)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED)
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitAtLevel1
        coEvery { triggerRepository.getCompletionsSince(habitId, any()) } returns listOf(
            makeTrigger(TriggerStatus.COMPLETED, triggerId + 1)
        )

        tracker.onCompleted(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `onCompleted skips promotion when autoAdjustLevel is false`() = runTest {
        val habitNoAuto = testHabit.copy(dedicationLevel = 0, autoAdjustLevel = false)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED)
        coEvery { habitRepository.getByIdOnce(habitId) } returns habitNoAuto

        tracker.onCompleted(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `onCompleted skips when habit already at level 5`() = runTest {
        val maxLevel = testHabit.copy(dedicationLevel = 5, autoAdjustLevel = true)
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED)
        coEvery { habitRepository.getByIdOnce(habitId) } returns maxLevel

        tracker.onCompleted(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `onCompleted trigger not found - skips promotion`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns null

        tracker.onCompleted(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `onCompleted trigger has no habitId - skips promotion`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED, hId = null)

        tracker.onCompleted(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }

    @Test
    fun `onCompleted habit not found - skips promotion`() = runTest {
        coEvery { triggerRepository.getById(triggerId) } returns makeTrigger(TriggerStatus.COMPLETED)
        coEvery { habitRepository.getByIdOnce(habitId) } returns null

        tracker.onCompleted(triggerId)

        coVerify(exactly = 0) { habitRepository.update(any()) }
    }
}
