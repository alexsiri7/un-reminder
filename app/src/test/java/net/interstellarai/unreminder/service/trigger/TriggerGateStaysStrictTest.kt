package net.interstellarai.unreminder.service.trigger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.DisplayTier
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.isDoableNow
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.ActivityResolution
import net.interstellarai.unreminder.domain.model.ActivityState
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalTime

/**
 * Ranking blocked habits for display must not loosen the trigger gate. The pipeline reads
 * eligibility from [HabitRepository.getEligibleHabits] — SQL, not [isDoableNow] — so this
 * runs both against one real database holding a habit that is out of its window.
 */
@RunWith(RobolectricTestRunner::class)
class TriggerGateStaysStrictTest {

    private lateinit var db: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var availabilityService: HabitAvailabilityService
    private lateinit var pipeline: TriggerPipeline
    private val notificationHelper: NotificationHelper = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        habitRepository = HabitRepository(db.habitDao(), db.habitLocationCrossRefDao(), db.habitWindowCrossRefDao())
        triggerRepository = TriggerRepository(db.triggerDao())
        val geofenceManager: GeofenceManager = mockk {
            every { currentLocationIds } returns MutableStateFlow<Set<Long>>(emptySet()).asStateFlow()
        }
        availabilityService = HabitAvailabilityService(
            habitRepository,
            WindowRepository(db.windowDao()),
            triggerRepository,
            geofenceManager,
        )
        pipeline = TriggerPipeline(
            habitRepository = habitRepository,
            triggerRepository = triggerRepository,
            locationRepository = mockk(),
            geofenceManager = geofenceManager,
            locationReconciler = mockk(relaxed = true),
            activityRecognitionManager = mockk {
                every { resolve() } returns ActivityResolution(ActivityState.Mode(ActivityMode.SITTING), null)
            },
            notificationHelper = notificationHelper,
            variationRepository = mockk(),
            refillScheduler = mockk(relaxUnitFun = true),
            levelDescriptionRepository = mockk(),
            widgetRefresher = widgetRefresher,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a habit shown as out of hours still never fires`() = runTest {
        val habitId = habitRepository.insert(HabitEntity(name = "stretch"))
        // A window on no day of the week is never open.
        val windowId = db.windowDao().insert(
            WindowEntity(startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MAX, daysOfWeekBitmask = 0),
        )
        habitRepository.setWindows(habitId, setOf(windowId))
        val habit = habitRepository.getByIdOnce(habitId)!!
        val triggerId = triggerRepository.insert(
            TriggerEntity(scheduledAt = Instant.now(), status = TriggerStatus.SCHEDULED),
        )

        assertEquals(mapOf(habitId to DisplayTier.OUT_OF_HOURS), availabilityService.computeDisplayTiers(listOf(habit)))
        assertFalse(availabilityService.computeAvailability(habit).isDoableNow)

        pipeline.execute(triggerId)

        assertEquals(emptyList<HabitEntity>(), habitRepository.getEligibleHabits(emptySet()))
        assertEquals(TriggerStatus.DISMISSED, triggerRepository.getById(triggerId)!!.status)
        verify(exactly = 0) { notificationHelper.postTriggerNotification(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { widgetRefresher.refresh() }
    }
}
