package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class EveningInvitationSettings(
    val enabled: Boolean = true,
    val time: LocalTime = EveningInvitationRepository.DEFAULT_TIME,
)

@Singleton
class EveningInvitationRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val enabledKey = booleanPreferencesKey("evening_invitation_enabled")
    private val minuteOfDayKey = intPreferencesKey("evening_invitation_minute_of_day")
    private val lastPostedEpochDayKey = longPreferencesKey("evening_invitation_last_posted_epoch_day")

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error, defaulting evening invitation settings", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    /** Emits the on/off switch and fire time. Defaults to on at [DEFAULT_TIME] on clean install. */
    val settings: Flow<EveningInvitationSettings> = prefs.map { p ->
        EveningInvitationSettings(
            enabled = p[enabledKey] ?: true,
            time = p[minuteOfDayKey]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: DEFAULT_TIME,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[enabledKey] = enabled }
    }

    suspend fun setTime(time: LocalTime) {
        dataStore.edit { p -> p[minuteOfDayKey] = time.toSecondOfDay() / 60 }
    }

    /** The local date the invitation was last posted on, or null if never. */
    suspend fun lastPostedDate(): LocalDate? =
        prefs.first()[lastPostedEpochDayKey]?.let { LocalDate.ofEpochDay(it) }

    suspend fun markPostedOn(date: LocalDate) {
        dataStore.edit { p -> p[lastPostedEpochDayKey] = date.toEpochDay() }
    }

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(20, 30)
        private const val TAG = "EveningInvitationRepository"
    }
}
