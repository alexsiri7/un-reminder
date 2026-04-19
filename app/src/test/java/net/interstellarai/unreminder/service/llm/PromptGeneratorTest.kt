package net.interstellarai.unreminder.service.llm

import android.content.Context
import net.interstellarai.unreminder.data.db.HabitEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class PromptGeneratorTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var tempDir: File
    private lateinit var generator: PromptGeneratorImpl

    private val habit = HabitEntity(
        id = 1,
        name = "meditation",
        fullDescription = "20-minute guided meditation",
        lowFloorDescription = "3 deep breaths",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        tempDir = createTempDir()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        generator = PromptGeneratorImpl(context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- null-engine paths (engine not initialized) ---

    @Test
    fun `generate returns fallback when engine is null`() = runTest {
        val result = generator.generate(habit, "Home", "morning")
        assertEquals("meditation: 3 deep breaths", result)
    }

    @Test
    fun `generate returns fallback with empty low floor description`() = runTest {
        val emptyHabit = habit.copy(id = 2, name = "exercise", lowFloorDescription = "")
        val result = generator.generate(emptyHabit, "Work", "afternoon")
        assertEquals("exercise: ", result)
    }

    @Test
    fun `fallback format is name colon lowFloorDescription`() = runTest {
        val customHabit = habit.copy(name = "reading", lowFloorDescription = "read one page")
        val result = generator.generate(customHabit, "any location", "evening")
        assertEquals("reading: read one page", result)
    }

    @Test
    fun `generateHabitFields throws when engine is null`() = runTest {
        val result = runCatching { generator.generateHabitFields("meditation") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("LLM unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `previewHabitNotification throws when engine is null`() = runTest {
        val result = runCatching { generator.previewHabitNotification(habit, "Anywhere") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("LLM unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `downloadProgress is null when model file is absent`() = runTest {
        assertNull(generator.downloadProgress.value)
        generator.initialize()
        assertNull(generator.downloadProgress.value)
    }
}
