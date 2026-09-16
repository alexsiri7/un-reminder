package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.interstellarai.unreminder.domain.model.SpendCapType
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Why the last background variant generation was refused, and when. */
data class GenerationFailure(
    val kind: Kind,
    /** Only meaningful for the spend-cap kinds; null when the Worker did not say. */
    val capType: SpendCapType?,
    val at: Instant,
) {
    enum class Kind {
        TOKEN_REJECTED,
        SPEND_CAP_USER,
        SPEND_CAP_GLOBAL,
        SERVICE_UNAVAILABLE,
    }
}

/**
 * The single most recent background generation failure, for Cloud AI settings and the habit
 * list banner to explain why the pool is not refilling (#422). One record, not one per habit:
 * every kind is Worker- or token-scoped, so any habit's successful refill proves the problem
 * is gone. Written by RefillWorker, cleared by its next success and by saving a new token
 * after a rejected one.
 *
 * Writes swallow failures: a failed note must never change what the worker does with the
 * failure it is noting, or turn a landed refill into a reported error.
 */
@Singleton
class GenerationFailureRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val kindKey = stringPreferencesKey("generation_failure_kind")
    private val capTypeKey = stringPreferencesKey("generation_failure_cap_type")
    private val atKey = longPreferencesKey("generation_failure_at")

    /** Emits the last failure, or null once a generation has succeeded since. */
    val failure: Flow<GenerationFailure?> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error, defaulting to no generation failure", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            val kind = prefs[kindKey]?.let { raw -> GenerationFailure.Kind.entries.firstOrNull { it.name == raw } }
            val at = prefs[atKey]?.let(Instant::ofEpochMilli)
            if (kind == null || at == null) null
            else GenerationFailure(kind, prefs[capTypeKey]?.let { raw -> SpendCapType.entries.firstOrNull { it.name == raw } }, at)
        }

    suspend fun record(failure: GenerationFailure) = write("record generation failure") { prefs ->
        prefs[kindKey] = failure.kind.name
        failure.capType?.let { prefs[capTypeKey] = it.name } ?: prefs.remove(capTypeKey)
        prefs[atKey] = failure.at.toEpochMilli()
    }

    suspend fun clear() = write("clear generation failure") { prefs ->
        prefs.remove(kindKey)
        prefs.remove(capTypeKey)
        prefs.remove(atKey)
    }

    private suspend fun write(what: String, transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not $what", e)
        }
    }

    companion object {
        private const val TAG = "GenerationFailureRepo"
    }
}
