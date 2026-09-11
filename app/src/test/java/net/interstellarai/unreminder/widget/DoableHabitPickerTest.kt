package net.interstellarai.unreminder.widget

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.service.notification.EmojiRotator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoableHabitPickerTest {

    private val habitRepository: HabitRepository = mockk()
    private val availabilityService: HabitAvailabilityService = mockk()
    private val picker = DoableHabitPicker(habitRepository, availabilityService, EmojiRotator())

    private fun habit(id: Long) = HabitEntity(id = id, name = "habit $id")

    private fun givenHabits(habits: List<HabitEntity>, availability: Map<Long, AvailabilityStatus>) {
        every { habitRepository.getAll() } returns flowOf(habits)
        coEvery { availabilityService.computeForAll(habits) } returns availability
    }

    private val unavailable = AvailabilityStatus.Unavailable(listOf(UnavailableReason.COMPLETED))

    @Test
    fun `picks only from eligible habits`() = runTest {
        val habits = (1L..4L).map { habit(it) }
        givenHabits(
            habits,
            mapOf(
                1L to unavailable,
                2L to AvailabilityStatus.Available,
                3L to AvailabilityStatus.NewHabit,
                4L to unavailable,
            ),
        )

        val picked = (1..50).map { picker.pick()!!.id }.toSet()

        assertTrue(picked.all { it in setOf(2L, 3L) })
        assertEquals(setOf(2L, 3L), picked)
    }

    @Test
    fun `carries the habit name and a stable emoji`() = runTest {
        givenHabits(listOf(habit(7L)), mapOf(7L to AvailabilityStatus.Available))

        val first = picker.pick()
        val second = picker.pick()

        assertEquals(DoableHabit(id = 7L, name = "habit 7", emoji = EmojiRotator().pick(7L)), first)
        assertEquals(first, second)
    }

    @Test
    fun `rests when nothing is eligible`() = runTest {
        val habits = listOf(habit(1L), habit(2L))
        givenHabits(habits, mapOf(1L to unavailable, 2L to unavailable))

        assertNull(picker.pick())
    }

    @Test
    fun `rests when there are no habits at all`() = runTest {
        givenHabits(emptyList(), emptyMap())

        assertNull(picker.pick())
    }
}
