package com.alexsiri7.unreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[ONBOARDING_DONE] ?: false }

    suspend fun markOnboardingCompleted() {
        dataStore.edit { prefs -> prefs[ONBOARDING_DONE] = true }
    }
}
