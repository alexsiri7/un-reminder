package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")

    /** Flow that emits whether the user has completed onboarding. Defaults to false on clean install. */
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error, defaulting to not-onboarded", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[onboardingDoneKey] ?: false }

    /** Persists onboarding completion so the onboarding screen is never shown again. */
    suspend fun markOnboardingCompleted() {
        dataStore.edit { prefs -> prefs[onboardingDoneKey] = true }
    }

    companion object {
        private const val TAG = "OnboardingRepository"
    }
}
