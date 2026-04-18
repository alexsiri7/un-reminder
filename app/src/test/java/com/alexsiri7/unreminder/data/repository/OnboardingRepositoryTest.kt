package com.alexsiri7.unreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRepositoryTest {

    private fun buildRepo(prefs: Preferences): OnboardingRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return OnboardingRepository(dataStore)
    }

    @Test
    fun `isOnboardingCompleted defaults to false when key is absent`() = runTest {
        val repo = buildRepo(mutablePreferencesOf())

        val result = repo.isOnboardingCompleted.first()

        assertFalse(result)
    }

    @Test
    fun `isOnboardingCompleted returns true when key is set`() = runTest {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("onboarding_done") to true
        )
        val repo = buildRepo(prefs)

        val result = repo.isOnboardingCompleted.first()

        assertTrue(result)
    }

    @Test
    fun `isOnboardingCompleted returns false when key is explicitly false`() = runTest {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("onboarding_done") to false
        )
        val repo = buildRepo(prefs)

        val result = repo.isOnboardingCompleted.first()

        assertFalse(result)
    }
}
