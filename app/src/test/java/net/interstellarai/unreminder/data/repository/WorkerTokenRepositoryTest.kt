package net.interstellarai.unreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val registered = "ur1_0123456789abcdef_" + "a".repeat(64)
    private val pasted = "ur1_fedcba9876543210_" + "b".repeat(64)
    private val at = Instant.parse("2026-09-01T10:00:00Z")

    @Test
    fun `a registered token round-trips with its label and time`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())

        repo.setRegisteredToken(registered, "Pixel 8", at)

        assertEquals(registered, repo.token.first())
        assertEquals(SelfRegistration("Pixel 8", at), repo.selfRegistration.first())
    }

    @Test
    fun `registering clears a pending retry`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())
        repo.deferRegistration(at)

        repo.setRegisteredToken(registered, "Pixel 8", at)

        assertNull(repo.registrationRetryAt.first())
    }

    @Test
    fun `pasting a token wipes the registration and the pending retry`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())
        repo.setRegisteredToken(registered, "Pixel 8", at)
        repo.deferRegistration(at)

        repo.setToken(pasted)

        assertEquals(pasted, repo.token.first())
        assertNull(repo.selfRegistration.first())
        assertNull(repo.registrationRetryAt.first())
    }

    @Test
    fun `clearIfCurrent removes the token it was given`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())
        repo.setRegisteredToken(registered, "Pixel 8", at)

        assertTrue(repo.clearIfCurrent(registered))

        assertEquals("", repo.token.first())
        assertNull(repo.selfRegistration.first())
    }

    @Test
    fun `clearIfCurrent leaves a different token intact`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())
        repo.setRegisteredToken(registered, "Pixel 8", at)

        assertFalse(repo.clearIfCurrent(pasted))

        assertEquals(registered, repo.token.first())
        assertEquals(SelfRegistration("Pixel 8", at), repo.selfRegistration.first())
    }

    @Test
    fun `selfRegistration is null when the token is blank`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())
        repo.setRegisteredToken("", "Pixel 8", at)

        assertNull(repo.selfRegistration.first())
    }

    @Test
    fun `deferRegistration is read back`() = runTest {
        val repo = WorkerTokenRepository(FakeDataStore())

        repo.deferRegistration(at)

        assertEquals(at, repo.registrationRetryAt.first())
    }
}
