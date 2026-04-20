package net.interstellarai.unreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerSettingsRepositoryTest {

    private fun buildRepo(prefs: Preferences): WorkerSettingsRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return WorkerSettingsRepository(dataStore)
    }

    @Test
    fun `effectiveWorkerUrl returns DataStore value when non-empty`() = runTest {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("worker_url") to "https://my-worker.example.com"
        )
        val repo = buildRepo(prefs)

        val result = repo.effectiveWorkerUrl.first()

        assertEquals("https://my-worker.example.com", result)
    }

    @Test
    fun `effectiveWorkerSecret returns DataStore value when non-empty`() = runTest {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("worker_secret") to "s3cret"
        )
        val repo = buildRepo(prefs)

        val result = repo.effectiveWorkerSecret.first()

        assertEquals("s3cret", result)
    }

    @Test
    fun `effectiveWorkerUrl falls back to BuildConfig when DataStore is empty`() = runTest {
        val repo = buildRepo(mutablePreferencesOf())

        val result = repo.effectiveWorkerUrl.first()

        assertEquals(net.interstellarai.unreminder.BuildConfig.WORKER_URL, result)
    }

    @Test
    fun `effectiveWorkerSecret falls back to BuildConfig when DataStore is empty`() = runTest {
        val repo = buildRepo(mutablePreferencesOf())

        val result = repo.effectiveWorkerSecret.first()

        assertEquals(net.interstellarai.unreminder.BuildConfig.WORKER_SECRET, result)
    }

    @Test
    fun `DataStore value overrides BuildConfig — workerUrl DataStore takes priority`() = runTest {
        val override = "https://override.example.com"
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("worker_url") to override
        )
        val repo = buildRepo(prefs)

        val result = repo.effectiveWorkerUrl.first()

        assertEquals(override, result)
        assertTrue(result.isNotBlank())
    }
}
