package net.interstellarai.unreminder.service.worker

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WorkerTokenRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GenerationVersionWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val token = MutableStateFlow("ur1_0123456789abcdef_" + "f".repeat(64))
    private val mockWorkerTokenRepository: WorkerTokenRepository = mockk {
        every { token } returns this@GenerationVersionWorkerTest.token
    }
    private val mockProxyClient: RequestyProxyClient = mockk()
    private val mockVariationRepository: VariationRepository = mockk()
    private val mockRefillScheduler: RefillScheduler = mockk(relaxUnitFun = true)

    private val worker = createWorker(workerUrl = "https://worker.example")

    private fun createWorker(workerUrl: String) = GenerationVersionWorker(
        mockContext,
        mockWorkerParams,
        workerUrl,
        mockWorkerTokenRepository,
        mockProxyClient,
        mockVariationRepository,
        mockRefillScheduler,
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `does nothing when the build has no worker URL`() = runTest {
        assertEquals(Result.success(), createWorker(workerUrl = "").doWork())

        coVerify(exactly = 0) { mockProxyClient.generationVersion(any()) }
        verify(exactly = 0) { mockRefillScheduler.enqueuePaced(any()) }
        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `does nothing when no token has been entered`() = runTest {
        token.value = ""

        assertEquals(Result.success(), worker.doWork())

        coVerify(exactly = 0) { mockProxyClient.generationVersion(any()) }
        verify(exactly = 0) { mockRefillScheduler.enqueuePaced(any()) }
    }

    @Test
    fun `does nothing when the worker predates generation versions`() = runTest {
        coEvery { mockProxyClient.generationVersion(any()) } returns VariationEntity.UNVERSIONED

        assertEquals(Result.success(), worker.doWork())

        coVerify(exactly = 0) { mockVariationRepository.habitIdsNeedingRegeneration(any()) }
        verify(exactly = 0) { mockRefillScheduler.enqueuePaced(any()) }
    }

    @Test
    fun `enqueues a paced refill for every habit whose pool is at another version`() = runTest {
        coEvery { mockProxyClient.generationVersion(any()) } returns 2
        coEvery { mockVariationRepository.habitIdsNeedingRegeneration(2) } returns listOf(1L, 2L)

        assertEquals(Result.success(), worker.doWork())

        verify(exactly = 1) { mockRefillScheduler.enqueuePaced(listOf(1L, 2L)) }
    }

    @Test
    fun `retries quietly when the network is unreachable`() = runTest {
        coEvery { mockProxyClient.generationVersion(any()) } throws IOException("offline")

        assertEquals(Result.retry(), worker.doWork())

        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `retries quietly on a server error`() = runTest {
        coEvery { mockProxyClient.generationVersion(any()) } throws WorkerError(503, "")

        assertEquals(Result.retry(), worker.doWork())

        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `fails and reports a client error`() = runTest {
        coEvery { mockProxyClient.generationVersion(any()) } throws WorkerError(401, "")

        assertEquals(Result.failure(), worker.doWork())

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `fails and reports an unexpected exception`() = runTest {
        coEvery { mockProxyClient.generationVersion(any()) } throws RuntimeException("boom")

        assertEquals(Result.failure(), worker.doWork())

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }
}
