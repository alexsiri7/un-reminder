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

    private var listenerRegistered = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            _updateDownloaded.value = true
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
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
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
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }
}
