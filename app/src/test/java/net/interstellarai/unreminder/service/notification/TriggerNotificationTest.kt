package net.interstellarai.unreminder.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun posted(triggerId: Long, spriteTag: String?): Notification {
        helper.postTriggerNotification(
            triggerId = triggerId,
            promptText = "body",
            habitName = "meditation",
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
    fun `the Did it and Dismiss action intents stay immutable`() {
        val notification = posted(triggerId = 42L, spriteTag = null)

        val actionFlags = listOf("Did it", "Dismiss").map { title ->
            val action = notification.actions.single { it.title == title }
            shadowOf(action.actionIntent).flags
        }
        for (flags in actionFlags) {
            assertNotEquals(0, flags and PendingIntent.FLAG_IMMUTABLE)
            assertEquals(0, flags and PendingIntent.FLAG_MUTABLE)
        }
    }
}
