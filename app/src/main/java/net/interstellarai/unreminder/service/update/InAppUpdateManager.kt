package net.interstellarai.unreminder.service.update

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "InAppUpdateManager"
    }

    private val appUpdateManager = AppUpdateManagerFactory.create(context)

    private val _updateDownloaded = MutableStateFlow(false)
    val updateDownloaded: StateFlow<Boolean> = _updateDownloaded.asStateFlow()

    // Register once: startUpdateCheck is called on every onResume; re-registering
    // the same listener accumulates duplicate callbacks from AppUpdateManager.
    private var listenerRegistered = false
    private var updateCheckStarted = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> _updateDownloaded.value = true
            InstallStatus.FAILED -> Log.w(TAG, "Update download failed: errorCode=${state.installErrorCode()}")
            else -> Unit
        }
    }

    fun startUpdateCheck(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        if (!listenerRegistered) {
            appUpdateManager.registerListener(installStateListener)
            listenerRegistered = true
        }
        if (updateCheckStarted) return
        updateCheckStarted = true
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    Log.i(TAG, "Stalled download detected on resume — prompting install")
                    _updateDownloaded.value = true
                    return@addOnSuccessListener
                }
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isFlexibleUpdateAllowed
                ) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                        )
                    } catch (e: android.content.IntentSender.SendIntentException) {
                        Log.w(TAG, "startUpdateFlowForResult failed (debug/sideload)", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "appUpdateInfo check failed", e)
            }
    }

    fun unregisterListener() {
        if (!listenerRegistered) return
        appUpdateManager.unregisterListener(installStateListener)
        listenerRegistered = false
    }

    fun completeUpdate() {
        unregisterListener()
        _updateDownloaded.value = false
        appUpdateManager.completeUpdate()
            .addOnFailureListener { e ->
                Log.w(TAG, "completeUpdate failed, resetting state", e)
                _updateDownloaded.value = false
            }
    }

    fun resetUpdateDownloaded() {
        _updateDownloaded.value = false
    }
}
