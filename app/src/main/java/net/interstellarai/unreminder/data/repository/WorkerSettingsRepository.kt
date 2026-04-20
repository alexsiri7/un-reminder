package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.interstellarai.unreminder.BuildConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val urlKey = stringPreferencesKey("worker_url")
    private val secretKey = stringPreferencesKey("worker_secret")

    val workerUrl: Flow<String> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error — defaulting to empty", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[urlKey] ?: "" }

    val workerSecret: Flow<String> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error — defaulting to empty", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[secretKey] ?: "" }

    val effectiveWorkerUrl: Flow<String> = workerUrl.map { ds ->
        ds.ifBlank { BuildConfig.WORKER_URL }
    }

    val effectiveWorkerSecret: Flow<String> = workerSecret.map { ds ->
        ds.ifBlank { BuildConfig.WORKER_SECRET }
    }

    suspend fun setWorkerUrl(url: String) {
        dataStore.edit { it[urlKey] = url }
    }

    suspend fun setWorkerSecret(secret: String) {
        dataStore.edit { it[secretKey] = secret }
    }

    companion object {
        private const val TAG = "WorkerSettingsRepo"
    }
}
