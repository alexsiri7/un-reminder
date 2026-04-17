package com.alexsiri7.unreminder.service.llm

import android.content.Context
import com.alexsiri7.unreminder.data.db.HabitEntity
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PromptGeneratorTest {

    private val context: Context = mockk(relaxed = true)
    private val generator = PromptGenerator(context)

    private val habit = HabitEntity(
        id = 1,
        name = "meditation",
        fullDescription = "20-minute guided meditation",
        lowFloorDescription = "3 deep breaths",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `generate returns fallback when model is null`() = runTest {
        // model is null by default (initialize() not called in unit tests)
        val result = generator.generate(habit, "Home", "morning")
        assertEquals("meditation: 3 deep breaths", result)
    }

    @Test
    fun `generate returns fallback with empty low floor description`() = runTest {
        val emptyHabit = habit.copy(
            id = 2,
            name = "exercise",
            lowFloorDescription = ""
        )
        val result = generator.generate(emptyHabit, "Work", "afternoon")
        assertEquals("exercise: ", result)
    }

    @Test
    fun `fallback format is name colon lowFloorDescription`() = runTest {
        val customHabit = habit.copy(
            name = "reading",
            lowFloorDescription = "read one page"
        )
        val result = generator.generate(customHabit, "any location", "evening")
        assertEquals("reading: read one page", result)
    }

    @Test
    fun `generateHabitFields throws when model is null`() = runTest {
        val result = runCatching {
            generator.generateHabitFields("meditation")
        }
        assertTrue(result.isFailure)
        assertTrue("Expected IllegalStateException, got ${result.exceptionOrNull()?.javaClass}", result.exceptionOrNull() is IllegalStateException)
        assertEquals("LLM unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `previewHabitNotification throws when model is null`() = runTest {
        val result = runCatching {
            generator.previewHabitNotification(habit, "Anywhere")
        }
        assertTrue(result.isFailure)
        assertTrue("Expected IllegalStateException, got ${result.exceptionOrNull()?.javaClass}", result.exceptionOrNull() is IllegalStateException)
        assertEquals("LLM unavailable", result.exceptionOrNull()?.message)
    }
}
