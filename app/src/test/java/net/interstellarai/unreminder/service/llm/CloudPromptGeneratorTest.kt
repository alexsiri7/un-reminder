package net.interstellarai.unreminder.service.llm

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CloudPromptGeneratorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRequestyProxyClient: RequestyProxyClient = mockk()
    private val mockWorkerSettings: WorkerSettingsRepository = mockk()

    private fun buildGenerator() = CloudPromptGenerator(mockRequestyProxyClient, mockWorkerSettings)

    // --- aiStatus ---

    @Test
    fun `aiStatus is Unavailable when url is blank`() = runTest(testDispatcher) {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("s3cr3t")
        val gen = buildGenerator()
        val job = launch { gen.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, gen.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Unavailable when secret is blank`() = runTest(testDispatcher) {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("")
        val gen = buildGenerator()
        val job = launch { gen.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, gen.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Ready when both url and secret are non-blank`() = runTest {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("s3cr3t")
        val gen = buildGenerator()
        // Use first { predicate } to wait for the upstream combine to emit on Dispatchers.IO.
        val status = gen.aiStatus.first { it == AiStatus.Ready }
        assertEquals(AiStatus.Ready, status)
    }

    // --- generateHabitFields ---

    @Test
    fun `generateHabitFields passes url and secret to client`() = runTest {
        val url = "https://worker.example.com"
        val secret = "s3cr3t"
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf(url)
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf(secret)
        coEvery { mockRequestyProxyClient.habitFields("yoga", url, secret) } returns
            AiHabitFields("30-min flow session", "5 sun salutations")

        val gen = buildGenerator()
        val result = gen.generateHabitFields("yoga")

        assertEquals("30-min flow session", result.fullDescription)
        assertEquals("5 sun salutations", result.lowFloorDescription)
    }

    @Test
    fun `generateHabitFields throws WorkerAuthException when url is blank`() = runTest {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("s3cr3t")
        val gen = buildGenerator()

        assertThrows(WorkerAuthException::class.java) {
            kotlinx.coroutines.runBlocking { gen.generateHabitFields("yoga") }
        }
    }

    @Test
    fun `generateHabitFields throws WorkerAuthException when secret is blank`() = runTest {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("")
        val gen = buildGenerator()

        assertThrows(WorkerAuthException::class.java) {
            kotlinx.coroutines.runBlocking { gen.generateHabitFields("yoga") }
        }
    }

    // --- previewHabitNotification ---

    @Test
    fun `previewHabitNotification passes habit and locationName to client`() = runTest {
        val url = "https://worker.example.com"
        val secret = "s3cr3t"
        val habit = HabitEntity(
            id = 1L, name = "yoga", fullDescription = "flow session",
            lowFloorDescription = "5 breaths", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf(url)
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf(secret)
        coEvery { mockRequestyProxyClient.preview(habit, "Home", url, secret) } returns "Roll out the mat now"

        val gen = buildGenerator()
        val result = gen.previewHabitNotification(habit, "Home")

        assertEquals("Roll out the mat now", result)
    }

    @Test
    fun `previewHabitNotification throws WorkerAuthException when url is blank`() = runTest {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("s3cr3t")
        val habit = HabitEntity(
            id = 1L, name = "yoga", fullDescription = "f", lowFloorDescription = "l",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val gen = buildGenerator()

        assertThrows(WorkerAuthException::class.java) {
            kotlinx.coroutines.runBlocking { gen.previewHabitNotification(habit, "Home") }
        }
    }

    // --- generate (stub) ---

    @Test
    fun `generate returns habit name (stub)`() = runTest {
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("")
        val habit = HabitEntity(
            id = 1L, name = "yoga", fullDescription = "f", lowFloorDescription = "l",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val gen = buildGenerator()
        assertEquals("yoga", gen.generate(habit, "Home", "morning"))
    }
}
