package net.interstellarai.unreminder.widget

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.service.notification.EmojiRotator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DoableHabitPickerTest {

    private val habitRepository: HabitRepository = mockk()
    private val availabilityService: HabitAvailabilityService = mockk()
    private val levelDescriptionRepository: HabitLevelDescriptionRepository = mockk()
    private val variationRepository: VariationRepository = mockk()
    private val picker = DoableHabitPicker(
        habitRepository,
        availabilityService,
        levelDescriptionRepository,
        variationRepository,
        EmojiRotator(),
    )

    @Before
    fun setup() {
        coEvery { levelDescriptionRepository.getDescriptionForLevel(any(), any()) } returns null
        coEvery { variationRepository.peekUnusedVariation(any()) } returns null
    }

    private fun habit(id: Long) = HabitEntity(id = id, name = "habit $id", dedicationLevel = 2)

    private fun variation(id: Long, habitId: Long, spriteTag: String? = null) = VariationEntity(
        id = id, habitId = habitId, text = "variant $id", promptFingerprint = "fp",
        generatedAt = Instant.EPOCH, spriteTag = spriteTag,
    )

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
    fun `carries the habit name, a stable emoji and the peeked variant with its sprite tag`() = runTest {
        givenHabits(listOf(habit(7L)), mapOf(7L to AvailabilityStatus.Available))
        coEvery { variationRepository.peekUnusedVariation(7L) } returns variation(70L, 7L, spriteTag = "chef_pan_flip")

        val first = picker.pick()
        val second = picker.pick()

        assertEquals(
            DoableHabit(
                id = 7L,
                name = "habit 7",
                emoji = EmojiRotator().pick(7L),
                text = "variant 70",
                variationId = 70L,
                spriteTag = "chef_pan_flip",
            ),
            first,
        )
        assertEquals(first, second)
    }

    @Test
    fun `a refresh only peeks and never consumes or refills`() = runTest {
        givenHabits(listOf(habit(7L)), mapOf(7L to AvailabilityStatus.Available))
        coEvery { variationRepository.peekUnusedVariation(7L) } returns variation(70L, 7L)

        repeat(3) { picker.pick() }

        coVerify(exactly = 3) { variationRepository.peekUnusedVariation(7L) }
        confirmVerified(variationRepository)
    }

    @Test
    fun `an empty pool falls back to the level description with no variant or tag`() = runTest {
        givenHabits(listOf(habit(7L)), mapOf(7L to AvailabilityStatus.Available))
        coEvery { levelDescriptionRepository.getDescriptionForLevel(7L, 2) } returns "five minutes"

        val picked = picker.pick()!!

        assertEquals("five minutes", picked.text)
        assertNull(picked.variationId)
        assertNull(picked.spriteTag)
    }

    @Test
    fun `a blank level description leaves the text empty rather than hiding the habit`() = runTest {
        givenHabits(listOf(habit(7L)), mapOf(7L to AvailabilityStatus.Available))
        coEvery { levelDescriptionRepository.getDescriptionForLevel(7L, 2) } returns ""

        val picked = picker.pick()!!

        assertEquals(7L, picked.id)
        assertNull(picked.text)
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
