package net.interstellarai.unreminder.service.llm

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.repository.WorkerTokenRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class CloudPromptGeneratorTest {

    private val workerUrl = "https://worker.example"
    private val validToken = "ur1_0123456789abcdef_" + "f".repeat(64)
    private val token = MutableStateFlow("")
    private val mockWorkerTokenRepository: WorkerTokenRepository = mockk {
        every { token } returns this@CloudPromptGeneratorTest.token
    }
    private val mockProxyClient: RequestyProxyClient = mockk()

    @Test
    fun `aiStatus becomes Ready once a token is saved, without recreating the generator`() = runTest {
        val generator = CloudPromptGenerator(mockProxyClient, mockWorkerTokenRepository, workerUrl, backgroundScope)
        runCurrent()
        assertEquals(AiStatus.Unavailable, generator.aiStatus.value)

        token.value = validToken
        runCurrent()

        assertEquals(AiStatus.Ready, generator.aiStatus.value)
    }

    @Test
    fun `aiStatus stays Unavailable when the build has no worker URL`() = runTest {
        token.value = validToken
        val generator = CloudPromptGenerator(mockProxyClient, mockWorkerTokenRepository, "", backgroundScope)
        runCurrent()

        assertEquals(AiStatus.Unavailable, generator.aiStatus.value)
    }

    @Test
    fun `generateHabitFields sends the saved token to the worker`() = runTest {
        token.value = validToken
        val fields = AiHabitFields(descriptionLadder = listOf("a", "b"))
        coEvery { mockProxyClient.habitFields("Read", workerUrl, validToken) } returns fields
        val generator = CloudPromptGenerator(mockProxyClient, mockWorkerTokenRepository, workerUrl, backgroundScope)

        assertEquals(fields, generator.generateHabitFields("Read"))
    }

    @Test
    fun `generateHabitFields throws LlmUnavailable when no token is saved`() = runTest {
        val generator = CloudPromptGenerator(mockProxyClient, mockWorkerTokenRepository, workerUrl, backgroundScope)

        assertFailsWith<LlmUnavailableException> { generator.generateHabitFields("Read") }
        coVerify(exactly = 0) { mockProxyClient.habitFields(any(), any(), any()) }
    }
}
