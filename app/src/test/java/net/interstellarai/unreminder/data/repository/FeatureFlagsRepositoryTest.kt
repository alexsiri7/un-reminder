package net.interstellarai.unreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FeatureFlagsRepositoryTest {

    private fun buildRepo(prefs: Preferences): FeatureFlagsRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return FeatureFlagsRepository(dataStore)
    }

    @Test
    fun `useCloudPool defaults to true when key is absent`() = runTest {
        val repo = buildRepo(mutablePreferencesOf())
        assertTrue(repo.useCloudPool.first())
    }

    @Test
    fun `useCloudPool returns stored true when key is explicitly true`() = runTest {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("use_cloud_variation_pool") to true
        )
        val repo = buildRepo(prefs)
        assertTrue(repo.useCloudPool.first())
    }

    @Test
    fun `useCloudPool returns stored false when key is explicitly false`() = runTest {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("use_cloud_variation_pool") to false
        )
        val repo = buildRepo(prefs)
        assertFalse(repo.useCloudPool.first())
    }

    @Test
    fun `useCloudPool returns true on IOException by falling back to emptyPreferences`() = runTest {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow { throw IOException("disk error") }
        }
        val repo = FeatureFlagsRepository(dataStore)
        assertTrue(repo.useCloudPool.first())
    }

    @Test
    fun `useCloudPool rethrows non-IOException`() = runTest {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow { throw RuntimeException("unexpected") }
        }
        val repo = FeatureFlagsRepository(dataStore)
        var caught: RuntimeException? = null
        try {
            repo.useCloudPool.first()
        } catch (e: RuntimeException) {
            caught = e
        }
        assertTrue("Expected RuntimeException to be rethrown", caught != null)
    }
}
