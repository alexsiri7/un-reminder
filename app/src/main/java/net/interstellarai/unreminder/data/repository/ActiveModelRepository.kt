package net.interstellarai.unreminder.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import net.interstellarai.unreminder.service.llm.ModelCatalog
import net.interstellarai.unreminder.service.llm.ModelDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent store for the currently-selected on-device LLM.
 *
 * A single string id is persisted under `active_model_id` in the shared
 * settings DataStore. Resolving the id into a [ModelDescriptor] is done
 * through [ModelCatalog.byId] so catalog additions/removals don't require a
 * migration — unknown ids simply fall back to [ModelCatalog.default].
 *
 * Kept separate from [ModelDownloadProgressRepository] because the two have
 * different lifecycles: the fraction clears on every download terminal state
 * whereas the active model selection persists forever (or until the user
 * changes it in Settings).
 */
@Singleton
class ActiveModelRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val activeIdKey = stringPreferencesKey(KEY_ACTIVE_ID)

    /**
     * Cold flow of the currently-selected descriptor. Emits [ModelCatalog.default]
     * on a fresh install (no key present) or when the persisted id refers to
     * a model no longer in the catalog (we removed it between app versions).
     */
    val active: Flow<ModelDescriptor> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read error — defaulting to catalog default", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[activeIdKey]?.let { ModelCatalog.byId(it) } ?: ModelCatalog.default }

    /**
     * Blocking one-shot read for synchronous call sites — [PromptGeneratorImpl]
     * uses this during `initialize()` so it knows which file to look for
     * before it can safely observe the flow.
     */
    suspend fun peek(): ModelDescriptor = active.first()

    /**
     * Persist the user's selection. The caller is responsible for enqueuing
     * the download — this repo only stores the choice so the next
     * `initialize()` can resolve it.
     */
    suspend fun setActive(id: String) {
        dataStore.edit { prefs -> prefs[activeIdKey] = id }
    }

    companion object {
        private const val TAG = "ActiveModelRepository"
        private const val KEY_ACTIVE_ID = "active_model_id"
    }
}
