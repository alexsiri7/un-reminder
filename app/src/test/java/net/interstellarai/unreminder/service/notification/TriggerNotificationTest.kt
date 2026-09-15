package net.interstellarai.unreminder.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import net.interstellarai.unreminder.MainActivity
import net.interstellarai.unreminder.domain.model.NotificationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TriggerNotificationTest {

    private lateinit var notificationManager: NotificationManager
    private lateinit var helper: NotificationHelper
    private val resolver = SpriteResolver()

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        helper = NotificationHelper(context, EmojiRotator(), resolver)
        helper.createNotificationChannel()
    }

    private fun posted(
        triggerId: Long,
        spriteTag: String?,
        actionUrl: String? = null,
        style: NotificationStyle = NotificationStyle.SPRITE,
        promptText: String = "body",
        treatmentSeed: Long = triggerId,
    ): Notification {
        helper.postTriggerNotification(
            triggerId = triggerId,
            promptText = promptText,
            habitName = "meditation",
            style = style,
            treatmentSeed = treatmentSeed,
            actionUrl = actionUrl,
            spriteTag = spriteTag,
        )
        return requireNotNull(shadowOf(notificationManager).getNotification(triggerId.toRequestCode())) {
            "trigger notification was not posted"
        }
    }

    private fun largeIconRes(notification: Notification): Int {
        val icon = requireNotNull(notification.getLargeIcon()) { "no large icon" }
        assertEquals(Icon.TYPE_RESOURCE, shadowOf(icon).type)
        return shadowOf(icon).resId
    }

    private fun templateOf(notification: Notification): String? = notification.extras.getString(Notification.EXTRA_TEMPLATE)

    private fun subTextOf(notification: Notification): String? =
        notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

    // A distinct trigger id per style so the notifications don't replace each other.
    private fun eachStyle(actionUrl: String? = null, promptText: String = "body"): Map<NotificationStyle, Notification> =
        NotificationStyle.entries.associateWith { style ->
            posted(triggerId = 100L + style.ordinal, spriteTag = null, actionUrl = actionUrl, style = style, promptText = promptText)
        }

    private fun habitLabel(triggerId: Long): String = "${EmojiRotator().pick(triggerId)} meditation"

    private fun textOf(notification: Notification): String? =
        notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun `large icon is the sprite tagged on the variant`() {
        val sprite = MascotSprites.entries[4]

        val notification = posted(triggerId = 42L, spriteTag = sprite.tag)

        assertEquals(sprite.drawableRes, largeIconRes(notification))
    }

    @Test
    fun `untagged variant still gets a large icon from the trigger rotation`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        assertEquals(resolver.resolve(null, rotationSeed = 42L), largeIconRes(notification))
    }

    @Test
    fun `the variant is the title and the habit label with its emoji is the text`() {
        val notification = posted(triggerId = 42L, spriteTag = MascotSprites.entries[0].tag)

        assertEquals("body", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(habitLabel(42L), textOf(notification))
    }

    // The title is never cut app-side and the body never repeats it: with no bigText the
    // expanded big-text view falls back to the content text, which is the habit label.
    @Test
    fun `every style keeps the whole variant as the title and never repeats it in the body`() {
        val prompt = "Two minutes of stillness before the next thing, the astronaut kind, drifting on."
        assertEquals(80, prompt.length)

        for ((style, notification) in eachStyle(promptText = prompt)) {
            assertEquals("$style", prompt, notification.extras.getString(Notification.EXTRA_TITLE))
            assertNull("$style", notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            assertEquals("$style", habitLabel(100L + style.ordinal), textOf(notification))
        }
    }

    // Only the geofence intent needs to be mutable (#339); the action receiver's must stay immutable.
    @Test
    fun `the Open and Later action intents stay immutable`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val actionFlags = listOf("Open", "Later").map { title ->
            val action = notification.actions.single { it.title == title }
            shadowOf(action.actionIntent).flags
        }
        for (flags in actionFlags) {
            assertNotEquals(0, flags and PendingIntent.FLAG_IMMUTABLE)
            assertEquals(0, flags and PendingIntent.FLAG_MUTABLE)
        }
    }

    @Test
    fun `swiping the trigger notification away records a dismissal`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val shadow = shadowOf(requireNotNull(notification.deleteIntent) { "no delete intent" })
        assertTrue(shadow.isBroadcast)
        val saved = shadow.savedIntent
        assertEquals(NotificationActionReceiver::class.java.name, saved.component?.className)
        assertEquals(
            NotificationHelper.ACTION_DISMISSED,
            saved.getStringExtra(NotificationHelper.EXTRA_ACTION)
        )
        assertEquals(42L, saved.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1L))
    }

    @Test
    fun `the delete intent is immutable and does not collapse with the Later action`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val deleteShadow = shadowOf(requireNotNull(notification.deleteIntent) { "no delete intent" })
        val laterShadow = shadowOf(notification.actions.single { it.title == "Later" }.actionIntent)
        assertNotEquals(deleteShadow.requestCode, laterShadow.requestCode)
        assertNotEquals(0, deleteShadow.flags and PendingIntent.FLAG_IMMUTABLE)
        assertEquals(0, deleteShadow.flags and PendingIntent.FLAG_MUTABLE)
    }

    // Request codes are part of the on-device contract: renumbering one orphans the
    // PendingIntent of any notification already on screen across an app update.
    @Test
    fun `each intent keeps the request code its slot allocates`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val requestCodeOf = { title: String ->
            shadowOf(notification.actions.single { it.title == title }.actionIntent).requestCode
        }
        assertEquals((NotificationHelper.NOTIFICATION_DETAIL_BASE + 42L).toRequestCode(), requestCodeOf("Open"))
        assertEquals(requestCodeOf("Open"), shadowOf(notification.contentIntent).requestCode)
        assertEquals((NotificationHelper.NOTIFICATION_LATER_BASE + 42L).toRequestCode(), requestCodeOf("Later"))
        assertEquals(
            (NotificationHelper.NOTIFICATION_DELETE_BASE + 42L).toRequestCode(),
            shadowOf(requireNotNull(notification.deleteIntent) { "no delete intent" }).requestCode
        )
    }

    @Test
    fun `trigger notifications carry exactly Open and Later with or without a video`() {
        val plain = posted(triggerId = 42L, spriteTag = null)
        val withVideo = posted(triggerId = 43L, spriteTag = null, actionUrl = "https://example.com/v")

        assertEquals(listOf("Open", "Later"), plain.actions.map { it.title })
        assertEquals(listOf("Open", "Later"), withVideo.actions.map { it.title })
    }

    @Test
    fun `Open opens the reminder detail for this trigger`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val shadow = shadowOf(notification.actions.single { it.title == "Open" }.actionIntent)
        assertTrue(shadow.isActivity)
        val saved = shadow.savedIntent
        assertEquals(MainActivity::class.java.name, saved.component?.className)
        assertTrue(saved.getBooleanExtra(NotificationHelper.EXTRA_OPEN_DETAIL, false))
        assertEquals(42L, saved.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1L))
    }

    @Test
    fun `a video-bearing variant shows the indicator in the sub text`() {
        val plain = posted(triggerId = 42L, spriteTag = null)
        val withVideo = posted(triggerId = 43L, spriteTag = null, actionUrl = "https://example.com/v")
        val insecure = posted(triggerId = 44L, spriteTag = null, actionUrl = "http://example.com/v")

        val subText = { n: Notification -> n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() }
        assertEquals(NotificationHelper.VIDEO_INDICATOR, subText(withVideo))
        assertNull(subText(plain))
        assertNull(subText(insecure))
        assertEquals(habitLabel(43L), textOf(withVideo))
    }

    @Test
    fun `tapping Later broadcasts ACTION_LATER for the trigger`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val shadow = shadowOf(notification.actions.single { it.title == "Later" }.actionIntent)
        assertTrue(shadow.isBroadcast)
        val saved = shadow.savedIntent
        assertEquals(NotificationActionReceiver::class.java.name, saved.component?.className)
        assertEquals(NotificationHelper.ACTION_LATER, saved.getStringExtra(NotificationHelper.EXTRA_ACTION))
        assertEquals(42L, saved.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1L))
    }

    @Test
    fun `every style carries exactly Open and Later with or without a video`() {
        for ((style, notification) in eachStyle()) {
            assertEquals("$style", listOf("Open", "Later"), notification.actions.map { it.title })
        }
        for ((style, notification) in eachStyle(actionUrl = "https://example.com/v")) {
            assertEquals("$style", listOf("Open", "Later"), notification.actions.map { it.title })
        }
    }

    // The header binds subText before any style summary, so the indicator only stays visible
    // in every style as long as no style sets a summary.
    @Test
    fun `every style shows the video indicator in the sub text and none sets a summary`() {
        for ((style, notification) in eachStyle(actionUrl = "https://example.com/v")) {
            assertEquals("$style", NotificationHelper.VIDEO_INDICATOR, subTextOf(notification))
            assertNull("$style", notification.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            assertEquals("$style", "body", notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("$style", habitLabel(100L + style.ordinal), textOf(notification))
        }
        for ((style, notification) in eachStyle()) {
            assertNull("$style", subTextOf(notification))
        }
    }

    @Test
    fun `every style keeps the detail content intent and the delete intent`() {
        for ((style, notification) in eachStyle()) {
            val triggerId = 100L + style.ordinal
            val requestCodeOf = { title: String ->
                shadowOf(notification.actions.single { it.title == title }.actionIntent).requestCode
            }
            assertEquals("$style", (NotificationHelper.NOTIFICATION_DETAIL_BASE + triggerId).toRequestCode(), requestCodeOf("Open"))
            assertEquals("$style", requestCodeOf("Open"), shadowOf(notification.contentIntent).requestCode)
            assertEquals("$style", (NotificationHelper.NOTIFICATION_LATER_BASE + triggerId).toRequestCode(), requestCodeOf("Later"))
            assertEquals(
                "$style",
                (NotificationHelper.NOTIFICATION_DELETE_BASE + triggerId).toRequestCode(),
                shadowOf(requireNotNull(notification.deleteIntent) { "$style has no delete intent" }).requestCode
            )
        }
    }

    @Test
    fun `SPRITE is big text with the sprite and the default colour`() {
        val notification = posted(triggerId = 42L, spriteTag = null, style = NotificationStyle.SPRITE)

        assertEquals(Notification.BigTextStyle::class.java.name, templateOf(notification))
        assertEquals(resolver.resolve(null, rotationSeed = 42L), largeIconRes(notification))
        assertEquals(Notification.COLOR_DEFAULT, notification.color)
    }

    @Test
    fun `BIG_PICTURE is a big picture of the sprite with the thumbnail hidden when expanded`() {
        val notification = posted(triggerId = 42L, spriteTag = null, style = NotificationStyle.BIG_PICTURE)

        assertEquals(Notification.BigPictureStyle::class.java.name, templateOf(notification))
        assertEquals(resolver.resolve(null, rotationSeed = 42L), largeIconRes(notification))
        val picture = notification.extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
            ?: notification.extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        assertNotNull("no big picture", picture)
        assertTrue(notification.extras.containsKey(Notification.EXTRA_LARGE_ICON_BIG))
        assertNull(notification.extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Icon::class.java))
    }

    @Test
    fun `TEXT_ONLY has no large icon and the sage accent`() {
        val notification = posted(triggerId = 42L, spriteTag = null, style = NotificationStyle.TEXT_ONLY)

        assertEquals(Notification.BigTextStyle::class.java.name, templateOf(notification))
        assertNull(notification.getLargeIcon())
        assertEquals(NotificationHelper.SAGE_ACCENT, notification.color)
    }

    // The trigger id is held fixed so the colour is proven to follow the variant's seed.
    @Test
    fun `ACCENT keeps the sprite and keys the header colour on the variant's seed`() {
        val colours = listOf(1L, 2L, 3L).map { seed ->
            val notification = posted(triggerId = 42L, spriteTag = null, style = NotificationStyle.ACCENT, treatmentSeed = seed)
            assertEquals(Notification.BigTextStyle::class.java.name, templateOf(notification))
            assertEquals(resolver.resolve(null, rotationSeed = 42L), largeIconRes(notification))
            assertEquals(NotificationHelper.ACCENT_PALETTE[seed.mod(NotificationHelper.ACCENT_PALETTE.size)], notification.color)
            notification.color
        }

        assertEquals(3, colours.toSet().size)
        assertTrue(NotificationHelper.SAGE_ACCENT !in colours)
    }

    @Test
    fun `a negative seed still picks an accent`() {
        val notification = posted(triggerId = 42L, spriteTag = null, style = NotificationStyle.ACCENT, treatmentSeed = -7L)

        assertTrue(notification.color in NotificationHelper.ACCENT_PALETTE)
    }
}
