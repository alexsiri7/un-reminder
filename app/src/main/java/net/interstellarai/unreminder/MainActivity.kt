package net.interstellarai.unreminder

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult
import dagger.hilt.android.AndroidEntryPoint
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.update.InAppUpdateManager
import net.interstellarai.unreminder.ui.navigation.NavGraph
import net.interstellarai.unreminder.ui.reminder.ReminderDetailTarget
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        /** The variant the widget's card asked to open, or null when [intent] asks for nothing of the kind. */
        internal fun variantTargetOf(intent: Intent): ReminderDetailTarget.Variant? {
            if (!intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_VARIANT, false)) return null
            val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
            if (habitId == -1L) {
                Log.w(TAG, "variantTargetOf: EXTRA_OPEN_VARIANT set but EXTRA_HABIT_ID missing")
                return null
            }
            val variationId = intent.getLongExtra(NotificationHelper.EXTRA_VARIATION_ID, -1L).takeIf { it != -1L }
            return ReminderDetailTarget.Variant(habitId, variationId)
        }
    }

    @Inject lateinit var inAppUpdateManager: InAppUpdateManager
    @Inject lateinit var notificationHelper: NotificationHelper

    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var pendingTimerTriggerId by mutableStateOf<Long?>(null)
    private var pendingDetailTriggerId by mutableStateOf<Long?>(null)
    private var pendingVariant by mutableStateOf<ReminderDetailTarget.Variant?>(null)
    private var pendingOpenNow by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleTimerIntent(intent)
        handleDetailIntent(intent)
        handleVariantIntent(intent)
        handleNowIntent(intent)

        updateLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED) {
                Log.w(TAG, "In-app update flow failed (resultCode=${result.resultCode})")
            }
        }

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val updateDownloaded by inAppUpdateManager.updateDownloaded.collectAsStateWithLifecycle()

            LaunchedEffect(updateDownloaded) {
                if (!updateDownloaded) return@LaunchedEffect
                val result = snackbarHostState.showSnackbar(
                    message = "Update ready",
                    actionLabel = "Restart",
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    inAppUpdateManager.completeUpdate()
                } else {
                    // User dismissed — reset so rotation doesn't immediately re-show the snackbar
                    inAppUpdateManager.resetUpdateDownloaded()
                }
            }

            UnReminderTheme {
                NavGraph(
                    snackbarHostState = snackbarHostState,
                    pendingTimerTriggerId = pendingTimerTriggerId,
                    onTimerNavigated = { pendingTimerTriggerId = null },
                    pendingDetailTriggerId = pendingDetailTriggerId,
                    onDetailNavigated = { pendingDetailTriggerId = null },
                    pendingVariant = pendingVariant,
                    onVariantNavigated = { pendingVariant = null },
                    pendingOpenNow = pendingOpenNow,
                    onNowNavigated = { pendingOpenNow = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTimerIntent(intent)
        handleDetailIntent(intent)
        handleVariantIntent(intent)
        handleNowIntent(intent)
    }

    private fun handleTimerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_TIMER, false) != true) return
        val id = intent.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1L)
        if (id != -1L) pendingTimerTriggerId = id
    }

    private fun handleDetailIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_DETAIL, false) != true) return
        val id = intent.getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1L)
        if (id != -1L) pendingDetailTriggerId = id
        else Log.w(TAG, "handleDetailIntent: EXTRA_OPEN_DETAIL set but EXTRA_TRIGGER_ID missing")
    }

    private fun handleVariantIntent(intent: Intent?) {
        val target = intent?.let(::variantTargetOf) ?: return
        pendingVariant = target
    }

    private fun handleNowIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_NOW, false) != true) return
        notificationHelper.cancelEveningInvitationIfOpenedFrom(intent)
        pendingOpenNow = true
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateManager.startUpdateCheck(this, updateLauncher)
    }

    override fun onStop() {
        super.onStop()
        inAppUpdateManager.unregisterListener()
    }
}
