package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val useCloudPoolKey = booleanPreferencesKey("use_cloud_variation_pool")

    val useCloudPool: Flow<Boolean> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error — defaulting use_cloud_pool to true", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[useCloudPoolKey] ?: true }

    suspend fun setUseCloudPool(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[useCloudPoolKey] = enabled }
    }

    companion object {
        private const val TAG = "FeatureFlagsRepository"
    }
}
