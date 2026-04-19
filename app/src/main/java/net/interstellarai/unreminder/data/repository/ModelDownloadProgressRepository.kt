package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent store for the last observed fraction of the on-device model
 * download.
 *
 * Why this exists: `PromptGeneratorImpl` keeps `_downloadProgress` in a
 * `StateFlow<Float?>` scoped to the application process. When Android kills
 * the process while the app is backgrounded — which it *will* do during a
 * 2.5 GB download — the StateFlow reverts to `null` on the next cold start
 * and the UI momentarily reads "0%" even though WorkManager is about to
 * resume the download with a `Range` header from the `.tmp` file's current
 * length. Writing the fraction to DataStore on every 1% update lets
 * `PromptGeneratorImpl.initialize()` seed `aiStatus = Downloading(fraction)`
 * immediately, before WorkManager's `getWorkInfosForUniqueWorkFlow` catches
 * up with the real state. Clears to `null` once the download terminates.
 */
@Singleton
class ModelDownloadProgressRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val fractionKey = floatPreferencesKey(KEY_FRACTION)

    /**
     * Cold flow of the last persisted fraction, or `null` when nothing is
     * stored (clean install / completed download). Swallows IOException to
     * stay safe on a corrupt prefs file — UI just sees "no progress yet".
     */
    val fraction: Flow<Float?> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error — treating as no persisted progress", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[fractionKey] }

    /** Blocking one-shot read for synchronous call sites (initialize()). */
    suspend fun peek(): Float? = fraction.first()

    /**
     * Writes the current download fraction. Callers throttle to every 1% so
     * DataStore isn't doing a flush on every byte — the worker already only
     * computes a new Int percent once per percentage point.
     */
    suspend fun write(fraction: Float) {
        dataStore.edit { prefs -> prefs[fractionKey] = fraction.coerceIn(0f, 1f) }
    }

    /** Clears the persisted value. Call on terminal states (success/failure). */
    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(fractionKey) }
    }

    companion object {
        private const val TAG = "ModelDownloadProgressRepository"
        // Underscore-prefixed so it can't collide with the preferences key naming
        // convention used by [OnboardingRepository] ("onboarding_done").
        private const val KEY_FRACTION = "model_download_fraction"
    }
}
