package net.interstellarai.unreminder.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WidgetRefreshWorker"
    }

    override suspend fun doWork(): Result {
        try {
            widgetRefresher.refreshNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Widget refresh failed", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "widget-refresh")
            }
        }
        return Result.success()
    }
}
