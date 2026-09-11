package net.interstellarai.unreminder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EveningInvitationRepositoryTest {

    private fun buildRepo(prefs: Preferences): EveningInvitationRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return EveningInvitationRepository(dataStore)
    }

    @Test
    fun `settings default to on at 20 30 when nothing is stored`() = runTest {
        val settings = buildRepo(mutablePreferencesOf()).settings.first()

        assertTrue(settings.enabled)
        assertEquals(LocalTime.of(20, 30), settings.time)
    }

    @Test
    fun `settings reflect stored switch and minute of day`() = runTest {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("evening_invitation_enabled") to false,
            intPreferencesKey("evening_invitation_minute_of_day") to 21 * 60 + 15,
        )

        val settings = buildRepo(prefs).settings.first()

        assertFalse(settings.enabled)
        assertEquals(LocalTime.of(21, 15), settings.time)
    }

    @Test
    fun `lastPostedDate is null when never posted`() = runTest {
        assertNull(buildRepo(mutablePreferencesOf()).lastPostedDate())
    }

    @Test
    fun `lastPostedDate reads back the stored epoch day`() = runTest {
        val date = LocalDate.of(2026, 9, 11)
        val prefs = mutablePreferencesOf(
            longPreferencesKey("evening_invitation_last_posted_epoch_day") to date.toEpochDay(),
        )

        assertEquals(date, buildRepo(prefs).lastPostedDate())
    }
}
