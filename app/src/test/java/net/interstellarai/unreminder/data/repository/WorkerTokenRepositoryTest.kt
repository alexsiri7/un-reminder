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
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerTokenRepositoryTest {

    private fun buildRepo(prefs: Preferences): WorkerTokenRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return WorkerTokenRepository(dataStore)
    }

    @Test
    fun `token defaults to empty when the key is absent`() = runTest {
        val repo = buildRepo(mutablePreferencesOf())

        assertEquals("", repo.token.first())
    }

    @Test
    fun `token returns the stored value`() = runTest {
        val stored = "ur1_0123456789abcdef_" + "f".repeat(64)
        val repo = buildRepo(mutablePreferencesOf(stringPreferencesKey("worker_token") to stored))

        assertEquals(stored, repo.token.first())
    }
}
