package com.alexsiri7.unreminder.service.trigger

import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.notification.NotificationHelper
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
        fullDescription = "20-minute session",
        lowFloorDescription = "3 deep breaths",
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
            TriggerEntity(id = 41, scheduledAt = Instant.now(), firedAt = Instant.now(), status = TriggerStatus.COMPLETED_FULL, habitId = habitId),
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
}
