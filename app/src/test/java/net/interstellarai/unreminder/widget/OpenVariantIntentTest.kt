package net.interstellarai.unreminder.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.interstellarai.unreminder.MainActivity
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.ui.reminder.ReminderDetailTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The widget's card intent, read back the way MainActivity reads it. */
@RunWith(RobolectricTestRunner::class)
class OpenVariantIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val habit = DoableHabit(id = 5L, name = "stretch", emoji = "🧘", text = "x", variationId = 50L, spriteTag = null)

    @Test
    fun `a shown variant opens as that variant of its habit`() {
        val intent = DoableHabitWidget.openVariantIntent(context, habit)

        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertEquals(ReminderDetailTarget.Variant(5L, 50L), MainActivity.variantTargetOf(intent))
    }

    @Test
    fun `a fallback habit opens with no variation`() {
        val intent = DoableHabitWidget.openVariantIntent(context, habit.copy(variationId = null))

        assertEquals(ReminderDetailTarget.Variant(5L, null), MainActivity.variantTargetOf(intent))
    }

    @Test
    fun `the Now deep link is not read as a variant`() {
        assertNull(MainActivity.variantTargetOf(DoableHabitWidget.openNowIntent(context)))
    }

    @Test
    fun `a variant intent without a habit id opens nothing`() {
        val intent = Intent(context, MainActivity::class.java).putExtra(NotificationHelper.EXTRA_OPEN_VARIANT, true)

        assertNull(MainActivity.variantTargetOf(intent))
    }

    @Test
    fun `the card opens the shown habit's variant`() {
        val intent = DoableHabitWidget.cardIntent(context, habit)

        assertEquals(ReminderDetailTarget.Variant(5L, 50L), MainActivity.variantTargetOf(intent))
        assertFalse(intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_NOW, false))
    }

    @Test
    fun `the card with no habit opens the Now menu`() {
        val intent = DoableHabitWidget.cardIntent(context, null)

        assertTrue(intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_NOW, false))
        assertTrue(intent.filterEquals(DoableHabitWidget.openNowIntent(context)))
        assertNull(MainActivity.variantTargetOf(intent))
    }

    @Test
    fun `two habits produce intents that filterEquals tells apart`() {
        val a = DoableHabitWidget.openVariantIntent(context, habit)
        val b = DoableHabitWidget.openVariantIntent(context, habit.copy(id = 6L, variationId = 60L))

        assertFalse(a.filterEquals(b))
        assertTrue(a.filterEquals(DoableHabitWidget.openVariantIntent(context, habit)))
    }
}
