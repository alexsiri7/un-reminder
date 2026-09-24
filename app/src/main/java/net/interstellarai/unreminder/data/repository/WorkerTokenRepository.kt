package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A token this install obtained from the Worker itself, rather than one pasted in. */
data class SelfRegistration(val deviceLabel: String, val registeredAt: Instant)

/**
 * The per-user Worker token: either self-registered through Play Integrity or pasted in Cloud AI
 * settings. This is the only source of the token: there is no build-time value for a blank entry
 * to shadow, so blank means the install has none yet.
 */
@Singleton
class WorkerTokenRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val tokenKey = stringPreferencesKey("worker_token")
    private val deviceLabelKey = stringPreferencesKey("worker_token_device_label")
    private val registeredAtKey = longPreferencesKey("worker_token_registered_at")
    private val retryAtKey = longPreferencesKey("registration_retry_at")

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error, defaulting to no worker token", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    /** Emits the stored token, or "" on a fresh install. */
    val token: Flow<String> = prefs.map { it[tokenKey] ?: "" }

    /** Non-null only while the stored token is one this install registered for itself. */
    val selfRegistration: Flow<SelfRegistration?> = prefs.map { prefs ->
        val label = prefs[deviceLabelKey]
        val at = prefs[registeredAtKey]
        if (label == null || at == null || prefs[tokenKey].isNullOrBlank()) null
        else SelfRegistration(label, Instant.ofEpochMilli(at))
    }

    /** Background registration attempts wait until this instant; null when they need not. */
    val registrationRetryAt: Flow<Instant?> = prefs.map { it[retryAtKey]?.let(Instant::ofEpochMilli) }

    /** A pasted token: whatever was registered before no longer describes it. */
    suspend fun setToken(value: String) {
        dataStore.edit { prefs ->
            prefs[tokenKey] = value
            prefs.removeRegistration()
            prefs.remove(retryAtKey)
        }
    }

    suspend fun setRegisteredToken(value: String, deviceLabel: String, at: Instant) {
        dataStore.edit { prefs ->
            prefs[tokenKey] = value
            prefs[deviceLabelKey] = deviceLabel
            prefs[registeredAtKey] = at.toEpochMilli()
            prefs.remove(retryAtKey)
        }
    }

    /**
     * Removes the token only if it is still [value], so a late rejection of an old token cannot
     * wipe one minted since. Returns whether it did.
     */
    suspend fun clearIfCurrent(value: String): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            if (prefs[tokenKey] == value) {
                prefs.remove(tokenKey)
                prefs.removeRegistration()
                cleared = true
            }
        }
        return cleared
    }

    suspend fun deferRegistration(until: Instant) {
        dataStore.edit { prefs -> prefs[retryAtKey] = until.toEpochMilli() }
    }

    private fun MutablePreferences.removeRegistration() {
        remove(deviceLabelKey)
        remove(registeredAtKey)
    }

    companion object {
        private const val TAG = "WorkerTokenRepository"
    }
}
