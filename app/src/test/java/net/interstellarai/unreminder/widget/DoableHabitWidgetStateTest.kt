package net.interstellarai.unreminder.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class DoableHabitWidgetStateTest {

    private val habit = DoableHabit(
        id = 7L,
        name = "stretch",
        emoji = "🧘",
        text = "Reach for the ceiling, pirate style.",
        variationId = 70L,
        spriteTag = "pirate_ship_rigging",
    )
    private val progress = DayProgress(completedToday = false, daysWithAnyCompletion = 4)

    @Test
    fun `a stored habit reads back whole`() {
        val prefs = mutablePreferencesOf()

        DoableHabitWidget.store(prefs, habit, progress, WidgetLayout.SPRITE_LEFT)

        assertEquals(habit, DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `storing no habit removes every key of the previous habit`() {
        val prefs = mutablePreferencesOf()
        DoableHabitWidget.store(prefs, habit, progress, WidgetLayout.SPRITE_LEFT)

        DoableHabitWidget.store(prefs, null, progress, WidgetLayout.SPRITE_LEFT)

        assertNull(DoableHabitWidget.stored(prefs))
        assertEquals(3, prefs.asMap().size)
        assertEquals(progress, DoableHabitWidget.storedDayProgress(prefs))
        assertEquals(WidgetLayout.SPRITE_LEFT, DoableHabitWidget.storedLayout(prefs))
    }

    @Test
    fun `a fallback habit reads back with no variant or tag`() {
        val prefs = mutablePreferencesOf()
        val fallback = habit.copy(text = "five minutes", variationId = null, spriteTag = null)

        DoableHabitWidget.store(prefs, fallback, progress, WidgetLayout.SPRITE_LEFT)

        assertEquals(fallback, DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `storing a fallback over a variant drops the variant's id and tag`() {
        val prefs = mutablePreferencesOf()
        DoableHabitWidget.store(prefs, habit, progress, WidgetLayout.SPRITE_LEFT)
        val fallback = habit.copy(text = null, variationId = null, spriteTag = null)

        DoableHabitWidget.store(prefs, fallback, progress, WidgetLayout.SPRITE_LEFT)

        assertEquals(fallback, DoableHabitWidget.stored(prefs))
    }

    @Test
    fun `nothing stored reads as no habit`() {
        assertNull(DoableHabitWidget.stored(mutablePreferencesOf()))
    }

    @Test
    fun `a habit and the day's progress land in one write`() {
        val prefs = mutablePreferencesOf()
        val done = DayProgress(completedToday = true, daysWithAnyCompletion = 12)

        DoableHabitWidget.store(prefs, habit, done, WidgetLayout.SPRITE_LEFT)

        assertEquals(habit, DoableHabitWidget.stored(prefs))
        assertEquals(done, DoableHabitWidget.storedDayProgress(prefs))
    }

    @Test
    fun `a refresh after completing flips the progress the same write that changes the habit`() {
        val prefs = mutablePreferencesOf()
        DoableHabitWidget.store(prefs, habit, progress, WidgetLayout.SPRITE_LEFT)

        DoableHabitWidget.store(prefs, null, progress.copy(completedToday = true, daysWithAnyCompletion = 5), WidgetLayout.SPRITE_LEFT)

        assertNull(DoableHabitWidget.stored(prefs))
        assertEquals(DayProgress(completedToday = true, daysWithAnyCompletion = 5), DoableHabitWidget.storedDayProgress(prefs))
    }

    @Test
    fun `day progress is unknown until the first refresh stores it`() {
        assertNull(DoableHabitWidget.storedDayProgress(mutablePreferencesOf()))
    }

    @Test
    fun `a done day reads as earned and carries the day count`() {
        assertEquals(
            "\u2713 today counts \u00b7 12 days so far",
            DoableHabitWidget.dayProgressLabel(DayProgress(completedToday = true, daysWithAnyCompletion = 12)),
        )
    }

    @Test
    fun `a not-yet day reads as an open invitation without loss language`() {
        val label = DoableHabitWidget.dayProgressLabel(DayProgress(completedToday = false, daysWithAnyCompletion = 12))

        assertEquals("today's still open \u00b7 12 days so far", label)
        for (loss in listOf("streak", "break", "risk", "lose", "miss")) {
            assertFalse("label must not imply loss: $label", label.contains(loss, ignoreCase = true))
        }
    }

    @Test
    fun `a single day is not pluralised, matching the Now page`() {
        assertEquals(
            "\u2713 today counts \u00b7 1 day so far",
            DoableHabitWidget.dayProgressLabel(DayProgress(completedToday = true, daysWithAnyCompletion = 1)),
        )
    }

    @Test
    fun `the stored layout reads back`() {
        val prefs = mutablePreferencesOf()

        DoableHabitWidget.store(prefs, habit, progress, WidgetLayout.TYPOGRAPHIC)

        assertEquals(WidgetLayout.TYPOGRAPHIC, DoableHabitWidget.storedLayout(prefs))
    }

    @Test
    fun `no stored layout reads as null`() {
        assertNull(DoableHabitWidget.storedLayout(mutablePreferencesOf()))
    }

    @Test
    fun `an unknown stored layout name reads as null`() {
        val prefs = mutablePreferencesOf()
        prefs[stringPreferencesKey("layout")] = "SPRITE_TOP"

        assertNull(DoableHabitWidget.storedLayout(prefs))
    }

    @Test
    fun `the next layout is never the one just shown`() {
        val prefs = mutablePreferencesOf()
        DoableHabitWidget.store(prefs, habit, progress, WidgetLayout.COMPACT)

        repeat(200) { assertNotEquals(WidgetLayout.COMPACT, DoableHabitWidget.nextLayout(prefs, Random(it))) }
    }

    @Test
    fun `a widget with no stored layout can start on any of the five`() {
        val seen = (0 until 200).map { DoableHabitWidget.nextLayout(mutablePreferencesOf(), Random(it)) }.toSet()

        assertEquals(WidgetLayout.entries.toSet(), seen)
    }

    // The refresher's exact expression: the roll must read the previous layout before the
    // write replaces it, or a widget could repeat itself.
    @Test
    fun `rolling into the same write never repeats the previous refresh's layout`() {
        val prefs = mutablePreferencesOf()
        var previous: WidgetLayout? = null

        repeat(200) {
            DoableHabitWidget.store(prefs, habit, progress, DoableHabitWidget.nextLayout(prefs, Random(it)))
            val current = DoableHabitWidget.storedLayout(prefs)
            assertNotEquals("refresh $it repeated $previous", previous, current)
            previous = current
        }
    }
}
