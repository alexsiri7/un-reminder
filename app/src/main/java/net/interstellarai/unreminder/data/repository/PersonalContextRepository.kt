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

@Singleton
class PersonalContextRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val personalContextKey = stringPreferencesKey("personal_context")

    /** Flow that emits the user's personal context string. Defaults to empty on clean install. */
    val personalContext: Flow<String> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error, defaulting to empty personal context", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[personalContextKey] ?: "" }

    /** Persists the user's personal context for use in future LLM prompts. */
    suspend fun setPersonalContext(value: String) {
        dataStore.edit { prefs -> prefs[personalContextKey] = value }
    }

    companion object {
        private const val TAG = "PersonalContextRepository"
    }
}
