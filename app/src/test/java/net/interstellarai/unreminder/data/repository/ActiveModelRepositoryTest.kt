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
import net.interstellarai.unreminder.service.llm.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveModelRepositoryTest {

    private fun buildRepo(prefs: Preferences): ActiveModelRepository {
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(prefs)
        }
        return ActiveModelRepository(dataStore)
    }

    @Test
    fun `active defaults to ModelCatalog_default when no key is stored`() = runTest {
        // Fresh-install contract: with no persisted id, users get the catalog
        // default without any extra wiring in PromptGeneratorImpl.
        val repo = buildRepo(mutablePreferencesOf())

        val result = repo.active.first()

        assertEquals(ModelCatalog.default, result)
    }

    @Test
    fun `active returns stored descriptor when key resolves in the catalog`() = runTest {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("active_model_id") to ModelCatalog.gemma3_1B_Task.id,
        )
        val repo = buildRepo(prefs)

        val result = repo.active.first()

        assertEquals(ModelCatalog.gemma3_1B_Task, result)
    }

    @Test
    fun `active falls back to default when persisted id is no longer in the catalog`() = runTest {
        // Catalog shrinkage scenario: we removed a model between app versions.
        // Users who picked that model must quietly revert to the current
        // default instead of seeing a crash or an "unknown" state.
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("active_model_id") to "retired-model-id",
        )
        val repo = buildRepo(prefs)

        val result = repo.active.first()

        assertEquals(ModelCatalog.default, result)
    }

    @Test
    fun `peek returns the same value the flow emits`() = runTest {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("active_model_id") to ModelCatalog.gemma4E2BLitertlm.id,
        )
        val repo = buildRepo(prefs)

        assertEquals(repo.active.first(), repo.peek())
    }

    @Test
    fun `setActive writes the id into DataStore`() = runTest {
        // Round-trip via the in-memory FakeDataStore: persistence contract is
        // "after setActive(X), the underlying prefs map has the active_model_id
        // key bound to X". The real DataStore is an async file-writer we can't
        // exercise in a pure unit test.
        val realStore = FakeDataStore()
        val repo = ActiveModelRepository(realStore)

        repo.setActive(ModelCatalog.gemma3_1B_Task.id)

        assertEquals(
            ModelCatalog.gemma3_1B_Task.id,
            realStore.snapshot()[stringPreferencesKey("active_model_id")],
        )
    }

    @Test
    fun `round-trip setActive then active returns the new descriptor`() = runTest {
        val realStore = FakeDataStore()
        val repo = ActiveModelRepository(realStore)

        repo.setActive(ModelCatalog.gemma3_1B_Task.id)
        val result = repo.active.first()

        assertEquals(ModelCatalog.gemma3_1B_Task, result)
    }
}

/**
 * Minimal in-memory DataStore<Preferences> for round-trip tests. Only supports
 * the two operations [ActiveModelRepository] needs: a `data` flow and
 * `updateData` (which `edit` delegates to). Kept private to this test file.
 */
private class FakeDataStore : DataStore<Preferences> {
    private var current: Preferences = mutablePreferencesOf()
    private val channel = kotlinx.coroutines.channels.Channel<Preferences>(
        kotlinx.coroutines.channels.Channel.CONFLATED,
    ).also { it.trySend(current) }

    override val data: kotlinx.coroutines.flow.Flow<Preferences> = kotlinx.coroutines.flow.flow {
        emit(current)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        current = transform(current)
        return current
    }

    fun snapshot(): Preferences = current
}
