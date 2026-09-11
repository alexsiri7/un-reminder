package net.interstellarai.unreminder.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.interstellarai.unreminder.MainActivity
import net.interstellarai.unreminder.widget.DoableHabitWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EveningInvitationNotificationTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var helper: NotificationHelper

    private val id = NotificationHelper.NOTIFICATION_ID_EVENING_INVITATION.toRequestCode()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        helper = NotificationHelper(context, EmojiRotator())
        helper.createNotificationChannel()
    }

    private fun posted(): Notification {
        helper.postEveningInvitation("body")
        return requireNotNull(shadowOf(notificationManager).getNotification(id)) { "invitation was not posted" }
    }

    private fun savedIntent(pendingIntent: PendingIntent): Intent = shadowOf(pendingIntent).savedIntent

    @Test
    fun `posts on its own channel with no delete intent`() {
        val notification = posted()

        assertEquals(NotificationHelper.CHANNEL_ID_INVITATION, notification.channelId)
        assertNull(notification.deleteIntent)
    }

    @Test
    fun `has Show me and Not tonight actions`() {
        val notification = posted()

        assertEquals(listOf("Show me", "Not tonight"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun `Show me and the body both open the app on the Now menu`() {
        val notification = posted()

        for (pendingIntent in listOf(notification.contentIntent, notification.actions[0].actionIntent)) {
            val intent = savedIntent(pendingIntent)
            assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
            assertTrue(intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_NOW, false))
            assertTrue(intent.getBooleanExtra(NotificationHelper.EXTRA_FROM_EVENING_INVITATION, false))
        }
    }

    @Test
    fun `opening the app from the invitation clears it`() {
        val notification = posted()

        helper.cancelEveningInvitationIfOpenedFrom(savedIntent(notification.contentIntent))

        assertNull(shadowOf(notificationManager).getNotification(id))
    }

    @Test
    fun `opening the app from the widget leaves the invitation posted`() {
        posted()
        val fromWidget = DoableHabitWidget.openNowIntent(context)
        assertTrue(fromWidget.getBooleanExtra(NotificationHelper.EXTRA_OPEN_NOW, false))

        helper.cancelEveningInvitationIfOpenedFrom(fromWidget)

        assertNotNull(shadowOf(notificationManager).getNotification(id))
    }

    @Test
    fun `Not tonight targets the dismiss receiver rather than the trigger action receiver`() {
        val notification = posted()

        val intent = savedIntent(notification.actions[1].actionIntent)
        assertEquals(ComponentName(context, EveningInvitationDismissReceiver::class.java), intent.component)
        assertNull(intent.extras)
    }

    @Test
    fun `cancelEveningInvitation clears the invitation and nothing else`() {
        posted()
        helper.postHabitPausedNotification(habitId = 3L, habitName = "stretch")
        val pausedId = (NotificationHelper.NOTIFICATION_ID_PAUSED_BASE + 3L).toRequestCode()

        helper.cancelEveningInvitation()

        assertNull(shadowOf(notificationManager).getNotification(id))
        assertNotNull(shadowOf(notificationManager).getNotification(pausedId))
    }

    @Test
    fun `dismiss receiver clears the invitation and nothing else`() {
        posted()
        helper.postHabitPausedNotification(habitId = 3L, habitName = "stretch")
        val pausedId = (NotificationHelper.NOTIFICATION_ID_PAUSED_BASE + 3L).toRequestCode()

        EveningInvitationDismissReceiver().onReceive(context, Intent())

        assertNull(shadowOf(notificationManager).getNotification(id))
        assertNotNull(shadowOf(notificationManager).getNotification(pausedId))
    }
}
