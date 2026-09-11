package net.interstellarai.unreminder.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoableHabitWidgetStateTest {

    private val habit = DoableHabit(id = 7L, name = "stretch", emoji = "🧘")

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
    fun `empty state reads as resting`() {
        assertNull(DoableHabitWidget.stored(mutablePreferencesOf()))
    }
}
