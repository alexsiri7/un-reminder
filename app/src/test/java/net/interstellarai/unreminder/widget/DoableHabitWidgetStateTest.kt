package net.interstellarai.unreminder.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoableHabitWidgetStateTest {

    private val habit = DoableHabit(
        id = 7L,
        name = "stretch",
        emoji = "🧘",
        text = "Reach for the ceiling, pirate style.",
        variationId = 70L,
        spriteTag = "pirate_ship_rigging",
    )

    @Test
    fun `a stored habit reads back whole`() {
        val prefs = mutablePreferencesOf()

        DoableHabitWidget.store(prefs, habit)

        assertEquals(habit, DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `storing the resting state removes every key of the previous habit`() {
        val prefs = mutablePreferencesOf()
        DoableHabitWidget.store(prefs, habit)

        DoableHabitWidget.store(prefs, null)

        assertTrue(prefs.asMap().isEmpty())
        assertNull(DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `a fallback habit reads back with no variant or tag`() {
        val prefs = mutablePreferencesOf()
        val fallback = habit.copy(text = "five minutes", variationId = null, spriteTag = null)

        DoableHabitWidget.store(prefs, fallback)

        assertEquals(fallback, DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `storing a fallback over a variant drops the variant's id and tag`() {
        val prefs = mutablePreferencesOf()
        DoableHabitWidget.store(prefs, habit)
        val fallback = habit.copy(text = null, variationId = null, spriteTag = null)

        DoableHabitWidget.store(prefs, fallback)

        assertEquals(fallback, DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `empty state reads as resting`() {
        assertNull(DoableHabitWidget.stored(mutablePreferencesOf()))
    }
}
