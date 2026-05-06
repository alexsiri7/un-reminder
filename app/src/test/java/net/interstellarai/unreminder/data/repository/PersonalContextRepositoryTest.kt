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

class PersonalContextRepositoryTest {

    private fun buildRepo(prefs: Preferences): PersonalContextRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return PersonalContextRepository(dataStore)
    }

    @Test
    fun `personalContext defaults to empty string when key is absent`() = runTest {
        val repo = buildRepo(mutablePreferencesOf())
        assertEquals("", repo.personalContext.first())
    }

    @Test
    fun `personalContext returns stored value when key is present`() = runTest {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("personal_context") to "use metrics"
        )
        val repo = buildRepo(prefs)
        assertEquals("use metrics", repo.personalContext.first())
    }

}
