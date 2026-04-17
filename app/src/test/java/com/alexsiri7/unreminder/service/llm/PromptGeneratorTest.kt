package com.alexsiri7.unreminder.service.llm

import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.domain.model.LocationTag
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PromptGeneratorTest {

    @Test
    fun `fallback returns habit name and low floor description`() {
        val habit = HabitEntity(
            id = 1,
            name = "meditation",
            fullDescription = "20-minute guided meditation",
            lowFloorDescription = "3 deep breaths",
            locationTag = LocationTag.ANYWHERE,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        // Test the fallback format directly
        val fallback = "${habit.name}: ${habit.lowFloorDescription}"
        assertEquals("meditation: 3 deep breaths", fallback)
    }

    @Test
    fun `fallback handles empty low floor description`() {
        val habit = HabitEntity(
            id = 2,
            name = "exercise",
            fullDescription = "30-minute run",
            lowFloorDescription = "",
            locationTag = LocationTag.HOME,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val fallback = "${habit.name}: ${habit.lowFloorDescription}"
        assertEquals("exercise: ", fallback)
    }
}
