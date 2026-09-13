package net.interstellarai.unreminder.widget

import android.content.Context
import android.util.Xml
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.interstellarai.unreminder.R
import net.interstellarai.unreminder.widget.DoableHabitWidget.Companion.FULL
import net.interstellarai.unreminder.widget.DoableHabitWidget.Companion.STRIP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * The size buckets are dp literals copied from doable_habit_widget_info.xml; these pin them
 * to what the provider file actually declares so neither can drift alone.
 */
@RunWith(RobolectricTestRunner::class)
class DoableHabitWidgetSizeBucketTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun declaredSize(widthAttr: Int, heightAttr: Int): DpSize {
        val parser = context.resources.getXml(R.xml.doable_habit_widget_info)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        val attrs = context.obtainStyledAttributes(Xml.asAttributeSet(parser), intArrayOf(widthAttr, heightAttr))
        val density = context.resources.displayMetrics.density
        try {
            return DpSize((attrs.getDimension(0, 0f) / density).dp, (attrs.getDimension(1, 0f) / density).dp)
        } finally {
            attrs.recycle()
        }
    }

    @Test
    fun `STRIP is the declared resize floor`() {
        assertEquals(declaredSize(android.R.attr.minResizeWidth, android.R.attr.minResizeHeight), STRIP)
    }

    @Test
    fun `FULL sits under the declared default size and above the strip`() {
        val default = declaredSize(android.R.attr.minWidth, android.R.attr.minHeight)

        assertTrue("FULL $FULL vs default $default", FULL.width < default.width && FULL.height < default.height)
        assertTrue("FULL $FULL vs STRIP $STRIP", FULL.width > STRIP.width && FULL.height > STRIP.height)
    }
}
