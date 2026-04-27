package net.interstellarai.unreminder.service.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.ScopeCallback
import io.sentry.protocol.SentryId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RefillWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockHabitRepository: HabitRepository = mockk()
    private val mockVariationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val mockProxyClient: RequestyProxyClient = mockk()
    private val mockWorkerSettings: WorkerSettingsRepository = mockk()

    private fun createWorker(habitId: Long = 1L): RefillWorker {
        val inputData = Data.Builder()
            .putLong(RefillWorker.KEY_HABIT_ID, habitId)
            .build()
        every { mockWorkerParams.inputData } returns inputData
        return RefillWorker(
            mockContext,
            mockWorkerParams,
            mockHabitRepository,
            mockVariationRepository,
            mockProxyClient,
            mockWorkerSettings,
        )
    }

    @Before
    fun setup() {
        coEvery { mockWorkerSettings.workerUrl } returns flowOf("https://worker.test")
        coEvery { mockWorkerSettings.workerSecret } returns flowOf("secret")
    }

    @Test
    fun `doWork returns failure when habitId is missing`() = runTest {
        val worker = createWorker(habitId = -1L)
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork returns failure when workerUrl is blank`() = runTest {
        coEvery { mockWorkerSettings.workerUrl } returns flowOf("")
        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork returns failure when workerSecret is blank`() = runTest {
        coEvery { mockWorkerSettings.workerSecret } returns flowOf("")
        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork returns failure when habit not found`() = runTest {
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns null
        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork inserts variants and returns success on happy path`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        val variants = (1..20).map { "variant $it" }
        coEvery {
            mockProxyClient.generateBatch(
                any(), any(), any(), any(), any(),
                workerUrl = "https://worker.test",
                workerSecret = "secret",
            )
        } returns variants

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) {
            mockVariationRepository.insertAll(match<List<VariationEntity>> { it.size == 20 })
        }
    }

    @Test
    fun `doWork prunes consumed variations before inserting new ones`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        val variants = (1..20).map { "variant $it" }
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } returns variants

        val worker = createWorker()
        worker.doWork()

        coVerify(ordering = Ordering.ORDERED) {
            mockVariationRepository.deleteConsumedForHabit(1L)
            mockVariationRepository.insertAll(any())
        }
    }

    @Test
    fun `doWork returns failure on SpendCapExceededException`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } throws SpendCapExceededException()

        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork returns failure on WorkerAuthException`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } throws WorkerAuthException()

        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork returns retry on IOException`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } throws IOException("network error")

        val worker = createWorker()
        assertEquals(Result.retry(), worker.doWork())
    }

    @Test
    fun `doWork returns retry on WorkerError with 500 code`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } throws WorkerError(500, "Internal Server Error")

        val worker = createWorker()
        assertEquals(Result.retry(), worker.doWork())
    }

    @Test
    fun `doWork returns failure on WorkerError with 400 code`() = runTest {
        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } throws WorkerError(400, "Bad Request")

        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
    }

    @Test
    fun `doWork returns failure and reports to Sentry on unexpected exception`() = runTest {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        val habit = HabitEntity(id = 1L, name = "Meditate")
        coEvery { mockHabitRepository.getByIdOnce(1L) } returns habit
        coEvery {
            mockProxyClient.generateBatch(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("unexpected failure")

        val worker = createWorker()
        assertEquals(Result.failure(), worker.doWork())
        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }
}
