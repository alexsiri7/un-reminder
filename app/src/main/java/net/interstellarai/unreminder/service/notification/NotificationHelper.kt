package net.interstellarai.unreminder.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import net.interstellarai.unreminder.R
import net.interstellarai.unreminder.domain.model.NotificationStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context,
    private val emojiRotator: EmojiRotator,
    private val spriteResolver: SpriteResolver,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    companion object {
        const val CHANNEL_ID = "un_reminder_triggers"
        const val CHANNEL_NAME = "Habit Triggers"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_ACTION = "action"
        // No button sends this since #378; pre-update notifications still on screen do.
        const val ACTION_COMPLETED = "COMPLETED"
        const val ACTION_DISMISSED = "DISMISSED"
        const val ACTION_LATER = "LATER"
        const val CHANNEL_ID_SYSTEM = "un_reminder_system"
        const val CHANNEL_NAME_SYSTEM = "Habit Status"
        const val CHANNEL_ID_INVITATION = "un_reminder_invitations"
        const val CHANNEL_NAME_INVITATION = "Evening Invitations"
        const val EXTRA_OPEN_TIMER = "open_timer"
        const val EXTRA_OPEN_DETAIL = "open_detail"
        const val EXTRA_OPEN_NOW = "open_now"
        // The widget also opens the Now menu; only the invitation's own taps may clear it.
        const val EXTRA_FROM_EVENING_INVITATION = "from_evening_invitation"
        // Content intent base — above the retired * 3 action-intent range.
        const val NOTIFICATION_CONTENT_BASE = 2_000_000L
        const val NOTIFICATION_DETAIL_BASE = 3_000_000L
        // Single fixed id: there is at most one evening invitation, and it is never
        // keyed by a trigger. Kept above DETAIL_BASE so it can't collide with per-trigger codes.
        const val NOTIFICATION_ID_EVENING_INVITATION = 4_000_000L
        // Swipe-away and Later intents each live in their own band. The per-trigger
        // `triggerId * 3 + {0 Did it, 1 Dismiss, 2 Watch}` band those buttons once used is fully
        // retired (#370, #378) and must never be reallocated: a PendingIntent's identity ignores
        // extras, and pre-update notifications may still be on screen.
        const val NOTIFICATION_DELETE_BASE = 5_000_000L
        const val NOTIFICATION_LATER_BASE = 6_000_000L
        // Header sub-text marking a variant that carries a video (#378).
        const val VIDEO_INDICATOR = "\u25B6 video"
        // ARGB copies of ui/theme/Color.kt's SageAccent and its sage-harmonised status hues; the
        // service layer stays free of Compose. The palette excludes sage so ACCENT never matches
        // TEXT_ONLY.
        internal val SAGE_ACCENT = 0xFF4D6B3A.toInt()
        internal val ACCENT_PALETTE = listOf(0xFF9A7A15.toInt(), 0xFF7A8A9A.toInt(), 0xFF8C6B4F.toInt())
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Stochastic habit nudges"
        }
        notificationManager.createNotificationChannel(channel)

        val systemChannel = NotificationChannel(
            CHANNEL_ID_SYSTEM,
            CHANNEL_NAME_SYSTEM,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Habit lifecycle status updates"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(systemChannel)

        val invitationChannel = NotificationChannel(
            CHANNEL_ID_INVITATION,
            CHANNEL_NAME_INVITATION,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "An evening nudge on days with nothing done yet"
        }
        notificationManager.createNotificationChannel(invitationChannel)
    }

    fun postTriggerNotification(
        triggerId: Long,
        promptText: String,
        habitName: String,
        style: NotificationStyle,
        actionUrl: String? = null,
        spriteTag: String? = null,
    ) {
        val emoji = emojiRotator.pick(triggerId)
        // One PendingIntent serves both the body tap and Open, as the invitation's "Show me" does.
        // The screen it opens records OPENED (ReminderDetailViewModel) and clears the notification.
        val detailIntent = Intent(context, net.interstellarai.unreminder.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TRIGGER_ID, triggerId)
            putExtra(EXTRA_OPEN_DETAIL, true)
        }
        val detailPendingIntent = PendingIntent.getActivity(
            context,
            (NOTIFICATION_DETAIL_BASE + triggerId).toRequestCode(),
            detailIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val laterIntent = createBroadcastIntent(
            triggerId,
            ACTION_LATER,
            (NOTIFICATION_LATER_BASE + triggerId).toRequestCode(),
        )
        // A swipe is the notification's only dismissal (#291, #370).
        val deleteIntent = createBroadcastIntent(
            triggerId,
            ACTION_DISMISSED,
            (NOTIFICATION_DELETE_BASE + triggerId).toRequestCode(),
        )

        // The system rounds the large icon's corners itself; the opaque tile goes in as-is, and
        // the same Icon doubles as the big picture.
        val sprite = Icon.createWithResource(context, spriteResolver.resolve(spriteTag, triggerId))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$emoji $habitName")
            .setContentText(promptText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDeleteIntent(deleteIntent)
            .setContentIntent(detailPendingIntent)
            .addAction(0, "Open", detailPendingIntent)
            .addAction(0, "Later", laterIntent)

        // The style is chosen and frozen by TriggerPipeline; this only dresses the builder. The
        // header binds subText before any style summary, so the video indicator survives every
        // style — never call setSummaryText here.
        when (style) {
            NotificationStyle.SPRITE -> builder
                .setLargeIcon(sprite)
                .setStyle(NotificationCompat.BigTextStyle().bigText(promptText))
            NotificationStyle.BIG_PICTURE -> builder
                .setLargeIcon(sprite)
                // bigLargeIcon(null): the expanded view otherwise shows the sprite twice.
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(sprite).bigLargeIcon(null as Icon?))
            NotificationStyle.TEXT_ONLY -> builder
                .setColor(SAGE_ACCENT)
                .setStyle(NotificationCompat.BigTextStyle().bigText(promptText))
            NotificationStyle.ACCENT -> builder
                .setLargeIcon(sprite)
                // .mod() (not %) keeps the index non-negative for a negative id.
                .setColor(ACCENT_PALETTE[triggerId.mod(ACCENT_PALETTE.size)])
                .setStyle(NotificationCompat.BigTextStyle().bigText(promptText))
        }

        // The video itself is watched from the variant view; the notification only flags it.
        if (actionUrl != null && actionUrl.startsWith("https://")) {
            builder.setSubText(VIDEO_INDICATOR)
        }

        notificationManager.notify(triggerId.toRequestCode(), builder.build())
    }

    fun postEveningInvitation(body: String) {
        val openNowIntent = Intent(context, net.interstellarai.unreminder.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOW, true)
            putExtra(EXTRA_FROM_EVENING_INVITATION, true)
        }
        val openNow = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_EVENING_INVITATION.toRequestCode(),
            openNowIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notTonight = PendingIntent.getBroadcast(
            context,
            (NOTIFICATION_ID_EVENING_INVITATION + 1).toRequestCode(),
            Intent(context, EveningInvitationDismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // No delete intent: swiping this away must stay a no-op for triggers and habits.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INVITATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(EveningInvitationWording.TITLE)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openNow)
            .addAction(0, "Show me", openNow)
            .addAction(0, "Not tonight", notTonight)
            .build()
        notificationManager.notify(NOTIFICATION_ID_EVENING_INVITATION.toRequestCode(), notification)
    }

    fun cancelNotification(triggerId: Long) {
        notificationManager.cancel(triggerId.toRequestCode())
    }

    fun cancelEveningInvitation() {
        notificationManager.cancel(NOTIFICATION_ID_EVENING_INVITATION.toRequestCode())
    }

    /** "Show me" is an action, so auto-cancel doesn't clear the invitation; the activity it opens does. */
    fun cancelEveningInvitationIfOpenedFrom(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_FROM_EVENING_INVITATION, false)) cancelEveningInvitation()
    }

    private fun createBroadcastIntent(triggerId: Long, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER_ID, triggerId)
            putExtra(EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
