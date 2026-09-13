package net.interstellarai.unreminder.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import net.interstellarai.unreminder.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun posted(triggerId: Long, spriteTag: String?, actionUrl: String? = null): Notification {
        helper.postTriggerNotification(
            triggerId = triggerId,
            promptText = "body",
            habitName = "meditation",
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
    fun `title keeps the emoji rotation alongside the sprite`() {
        val notification = posted(triggerId = 42L, spriteTag = MascotSprites.entries[0].tag)

        assertEquals("${EmojiRotator().pick(42L)} meditation", notification.extras.getString(Notification.EXTRA_TITLE))
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
        assertEquals("body", withVideo.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
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
}
