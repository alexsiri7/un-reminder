package net.interstellarai.unreminder

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
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult
import dagger.hilt.android.AndroidEntryPoint
import net.interstellarai.unreminder.service.update.InAppUpdateManager
import net.interstellarai.unreminder.ui.navigation.NavGraph
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject lateinit var inAppUpdateManager: InAppUpdateManager

    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                NavGraph(snackbarHostState = snackbarHostState)
            }
        }
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
