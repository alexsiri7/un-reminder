package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.domain.model.SpendCapType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class GenerationFailureRepositoryTest {

    private val kindKey = stringPreferencesKey("generation_failure_kind")
    private val capTypeKey = stringPreferencesKey("generation_failure_cap_type")
    private val atKey = longPreferencesKey("generation_failure_at")

    /** Enough of a Preferences DataStore for `edit` to round-trip; [failWrites] makes it a bad disk. */
    private class FakePreferencesDataStore(var failWrites: Boolean = false) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(preferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("disk full")
            return transform(state.value).also { state.value = it }
        }
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun buildRepo(prefs: Preferences): GenerationFailureRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return GenerationFailureRepository(dataStore)
    }

    @Test
    fun `failure is null when nothing is recorded`() = runTest {
        assertNull(buildRepo(mutablePreferencesOf()).failure.first())
    }

    @Test
    fun `failure reads kind, cap type and time back`() = runTest {
        val repo = buildRepo(
            mutablePreferencesOf(kindKey to "SPEND_CAP_USER", capTypeKey to "DAILY", atKey to 1_700_000_000_000L)
        )

        assertEquals(
            GenerationFailure(GenerationFailure.Kind.SPEND_CAP_USER, SpendCapType.DAILY, Instant.ofEpochMilli(1_700_000_000_000L)),
            repo.failure.first(),
        )
    }

    @Test
    fun `failure is null when the stored kind is unknown`() = runTest {
        val repo = buildRepo(mutablePreferencesOf(kindKey to "SOMETHING_NEWER", atKey to 1_700_000_000_000L))

        assertNull(repo.failure.first())
    }

    @Test
    fun `failure has a null cap type when the stored one is unknown`() = runTest {
        val repo = buildRepo(
            mutablePreferencesOf(kindKey to "SPEND_CAP_GLOBAL", capTypeKey to "HOURLY", atKey to 1_700_000_000_000L)
        )

        assertEquals(
            GenerationFailure(GenerationFailure.Kind.SPEND_CAP_GLOBAL, null, Instant.ofEpochMilli(1_700_000_000_000L)),
            repo.failure.first(),
        )
    }

    @Test
    fun `record then clear round-trips through the store`() = runTest {
        val repo = GenerationFailureRepository(FakePreferencesDataStore())
        val failure = GenerationFailure(GenerationFailure.Kind.TOKEN_REJECTED, null, Instant.ofEpochMilli(42_000L))

        repo.record(failure)
        assertEquals(failure, repo.failure.first())

        repo.clear()
        assertNull(repo.failure.first())
    }

    @Test
    fun `recording a failure without a cap type drops the previous one`() = runTest {
        val repo = GenerationFailureRepository(FakePreferencesDataStore())
        repo.record(GenerationFailure(GenerationFailure.Kind.SPEND_CAP_USER, SpendCapType.MONTHLY, Instant.ofEpochMilli(1L)))

        repo.record(GenerationFailure(GenerationFailure.Kind.SERVICE_UNAVAILABLE, null, Instant.ofEpochMilli(2L)))

        assertEquals(
            GenerationFailure(GenerationFailure.Kind.SERVICE_UNAVAILABLE, null, Instant.ofEpochMilli(2L)),
            repo.failure.first(),
        )
    }

    @Test
    fun `record and clear swallow a DataStore IOException`() = runTest {
        val repo = GenerationFailureRepository(FakePreferencesDataStore(failWrites = true))

        repo.record(GenerationFailure(GenerationFailure.Kind.TOKEN_REJECTED, null, Instant.ofEpochMilli(1L)))
        repo.clear()

        assertNull(repo.failure.first())
    }
}
