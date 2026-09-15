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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The per-user Worker token entered in Cloud AI settings. This is the only source of the
 * token: there is no build-time value for a blank entry to shadow, so blank means the user
 * has not entered one yet.
 */
@Singleton
class WorkerTokenRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val tokenKey = stringPreferencesKey("worker_token")

    /** Emits the stored token, or "" on a fresh install. */
    val token: Flow<String> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error, defaulting to no worker token", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[tokenKey] ?: "" }

    suspend fun setToken(value: String) {
        dataStore.edit { prefs -> prefs[tokenKey] = value }
    }

    companion object {
        private const val TAG = "WorkerTokenRepository"
    }
}
